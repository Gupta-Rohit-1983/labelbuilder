package com.rohit.labelbuilder.data.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A SQL statement with {@code :name} placeholders, rewritten to the positional {@code ?} form JDBC
 * wants (FR-DS-06).
 *
 * <p>Named rather than positional because the template binds parameters by name —
 * {@code "parameters": [ { "name": "sku", "boundTo": "${Prompt.SKU}" } ]} in lbl-format.md §5 — and
 * because inserting a column into a query should not silently re-aim its parameters at the wrong
 * values.
 *
 * <p><b>Always a placeholder, never string substitution.</b> Pasting an operator's value into SQL is
 * injection, and here the operator is often a warehouse PC that reads the value off a scanner. The
 * same reasoning as risk R-04 for expressions: the dangerous construct is not made safe, it is made
 * unavailable. {@link #sql()} is the only text that reaches the driver, and values travel as bound
 * parameters.
 *
 * <p>Parsing is lexical, and skips the places a colon means something else: inside
 * {@code 'literals'}, inside {@code "quoted identifiers"} and SQL Server's {@code [bracketed names]},
 * inside line and block comments, and in PostgreSQL's {@code ::type} cast.
 *
 * @param sql the statement with every {@code :name} replaced by {@code ?}
 * @param parameterNames the names in the order their placeholders appear, with repeats kept — a name
 *     used twice is bound twice, so this list indexes one-to-one onto the {@code ?}s
 */
public record NamedQuery(String sql, List<String> parameterNames) {

    public NamedQuery {
        Objects.requireNonNull(sql, "sql");
        parameterNames = List.copyOf(Objects.requireNonNull(parameterNames, "parameterNames"));
    }

    public static NamedQuery parse(String statement) {
        Objects.requireNonNull(statement, "statement");
        StringBuilder rewritten = new StringBuilder(statement.length());
        List<String> names = new ArrayList<>();
        int i = 0;
        while (i < statement.length()) {
            char c = statement.charAt(i);
            int skipTo =
                    switch (c) {
                        case '\'' -> copyUntil(statement, rewritten, i, "'");
                        case '"' -> copyUntil(statement, rewritten, i, "\"");
                        case '[' -> copyUntil(statement, rewritten, i, "]"); // SQL Server's [bracketed name]
                        default -> -1;
                    };
            if (skipTo >= 0) {
                i = skipTo;
                continue;
            }
            if (c == '-' && next(statement, i) == '-') {
                i = copyLineComment(statement, rewritten, i);
                continue;
            }
            if (c == '/' && next(statement, i) == '*') {
                i = copyBlockComment(statement, rewritten, i);
                continue;
            }
            if (c == ':' && next(statement, i) == ':') {
                rewritten.append("::"); // a cast, not a parameter
                i += 2;
                continue;
            }
            if (c == ':' && isNameStart(next(statement, i))) {
                int end = i + 1;
                while (end < statement.length() && isNamePart(statement.charAt(end))) {
                    end++;
                }
                names.add(statement.substring(i + 1, end));
                rewritten.append('?');
                i = end;
                continue;
            }
            rewritten.append(c);
            i++;
        }
        return new NamedQuery(rewritten.toString(), names);
    }

    public boolean hasParameters() {
        return !parameterNames.isEmpty();
    }

    /** The distinct names a caller has to supply values for, in first-seen order. */
    public List<String> distinctParameterNames() {
        return parameterNames.stream().distinct().toList();
    }

    /**
     * Copies a quoted run verbatim, including the quote characters.
     *
     * <p>A doubled quote is an escaped one and does not end the run, which is how {@code 'it''s'} stays
     * one literal.
     */
    private static int copyUntil(String statement, StringBuilder out, int start, String closing) {
        char close = closing.charAt(0);
        out.append(statement.charAt(start));
        int i = start + 1;
        while (i < statement.length()) {
            char c = statement.charAt(i);
            out.append(c);
            i++;
            if (c == close) {
                if (i < statement.length() && statement.charAt(i) == close) {
                    out.append(close);
                    i++;
                    continue;
                }
                return i;
            }
        }
        return i; // unterminated: the driver will say so more usefully than we could
    }

    private static int copyLineComment(String statement, StringBuilder out, int start) {
        int end = statement.indexOf('\n', start);
        if (end < 0) {
            out.append(statement, start, statement.length());
            return statement.length();
        }
        out.append(statement, start, end + 1);
        return end + 1;
    }

    private static int copyBlockComment(String statement, StringBuilder out, int start) {
        int end = statement.indexOf("*/", start + 2);
        if (end < 0) {
            out.append(statement, start, statement.length());
            return statement.length();
        }
        out.append(statement, start, end + 2);
        return end + 2;
    }

    private static char next(String statement, int i) {
        return i + 1 < statement.length() ? statement.charAt(i + 1) : '\0';
    }

    private static boolean isNameStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
