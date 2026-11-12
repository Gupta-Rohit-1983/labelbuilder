package com.rohit.labelbuilder.data.query;

import com.rohit.labelbuilder.data.jdbc.TableRef;
import java.util.Objects;
import java.util.Optional;

/**
 * A join onto another table, which is how a label gets a product's category name rather than its
 * category id.
 *
 * <p>{@link JoinType#LEFT} is the one that needs thinking about. An inner join silently drops the rows
 * whose lookup is missing, so a product with no category simply never gets a label — a run short by
 * three cartons with nothing to show why. A left join keeps the row and leaves the looked-up field
 * blank, which is visible. The builder offers both and the UI should default to LEFT.
 *
 * @param alias a short name to qualify the joined table's columns with, empty to use its own name
 */
public record JoinSpec(JoinType type, TableRef table, Optional<String> alias, QueryColumn left, QueryColumn right) {

    public JoinSpec {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
    }

    public static JoinSpec inner(TableRef table, QueryColumn left, QueryColumn right) {
        return new JoinSpec(JoinType.INNER, table, Optional.empty(), left, right);
    }

    public static JoinSpec left(TableRef table, QueryColumn left, QueryColumn right) {
        return new JoinSpec(JoinType.LEFT, table, Optional.empty(), left, right);
    }

    public JoinSpec as(String alias) {
        return new JoinSpec(type, table, Optional.of(alias), left, right);
    }

    /** The name the joined table's columns are qualified by. */
    public String qualifier() {
        return alias.orElseGet(table::name);
    }

    /** How the joined rows are matched. */
    public enum JoinType {
        INNER("INNER JOIN"),
        LEFT("LEFT JOIN");

        private final String sql;

        JoinType(String sql) {
            this.sql = sql;
        }

        public String sql() {
            return sql;
        }
    }
}
