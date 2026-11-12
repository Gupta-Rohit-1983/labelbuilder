package com.rohit.labelbuilder.data.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NamedQueryTest {

    @Test
    void namedPlaceholdersBecomePositionalOnes() {
        NamedQuery query = NamedQuery.parse("SELECT * FROM products WHERE sku = :sku AND active = :active");

        assertThat(query.sql()).isEqualTo("SELECT * FROM products WHERE sku = ? AND active = ?");
        assertThat(query.parameterNames()).containsExactly("sku", "active");
        assertThat(query.hasParameters()).isTrue();
    }

    @Test
    void aQueryWithoutParametersIsLeftAlone() {
        NamedQuery query = NamedQuery.parse("SELECT code, name FROM products ORDER BY code");

        assertThat(query.sql()).isEqualTo("SELECT code, name FROM products ORDER BY code");
        assertThat(query.parameterNames()).isEmpty();
        assertThat(query.hasParameters()).isFalse();
    }

    @Test
    void aNameUsedTwiceIsBoundTwice() {
        // The list indexes one-to-one onto the ?s, so a repeat has to appear twice even though the
        // caller supplies one value for it.
        NamedQuery query = NamedQuery.parse("SELECT * FROM t WHERE a = :x OR b = :x");

        assertThat(query.sql()).isEqualTo("SELECT * FROM t WHERE a = ? OR b = ?");
        assertThat(query.parameterNames()).containsExactly("x", "x");
        assertThat(query.distinctParameterNames()).containsExactly("x");
    }

    @Test
    void aColonInsideAStringLiteralIsNotAParameter() {
        NamedQuery query = NamedQuery.parse("SELECT 'see :note here' AS c FROM t WHERE x = :x");

        assertThat(query.sql()).isEqualTo("SELECT 'see :note here' AS c FROM t WHERE x = ?");
        assertThat(query.parameterNames()).containsExactly("x");
    }

    @Test
    void aDoubledQuoteInsideALiteralDoesNotEndIt() {
        NamedQuery query = NamedQuery.parse("SELECT 'it''s :not a param' FROM t");

        assertThat(query.sql()).isEqualTo("SELECT 'it''s :not a param' FROM t");
        assertThat(query.parameterNames()).isEmpty();
    }

    @Test
    void aColonInsideAQuotedIdentifierIsNotAParameter() {
        NamedQuery query = NamedQuery.parse("SELECT \"odd:column\" FROM t WHERE x = :x");

        assertThat(query.sql()).isEqualTo("SELECT \"odd:column\" FROM t WHERE x = ?");
        assertThat(query.parameterNames()).containsExactly("x");
    }

    @Test
    void aColonInsideASqlServerBracketedNameIsNotAParameter() {
        NamedQuery query = NamedQuery.parse("SELECT [odd:column] FROM [dbo].[t] WHERE x = :x");

        assertThat(query.sql()).isEqualTo("SELECT [odd:column] FROM [dbo].[t] WHERE x = ?");
        assertThat(query.parameterNames()).containsExactly("x");
    }

    @Test
    void aColonInALineCommentIsNotAParameter() {
        NamedQuery query = NamedQuery.parse("""
                SELECT * FROM t -- filter by :sku one day
                WHERE active = :active""");

        assertThat(query.sql()).contains("-- filter by :sku one day");
        assertThat(query.parameterNames()).containsExactly("active");
    }

    @Test
    void aColonInABlockCommentIsNotAParameter() {
        NamedQuery query = NamedQuery.parse("SELECT /* was :sku */ * FROM t WHERE x = :x");

        assertThat(query.sql()).isEqualTo("SELECT /* was :sku */ * FROM t WHERE x = ?");
        assertThat(query.parameterNames()).containsExactly("x");
    }

    @Test
    void aPostgresCastIsNotAParameter() {
        NamedQuery query = NamedQuery.parse("SELECT qty::numeric FROM t WHERE sku = :sku");

        assertThat(query.sql()).isEqualTo("SELECT qty::numeric FROM t WHERE sku = ?");
        assertThat(query.parameterNames()).containsExactly("sku");
    }

    @Test
    void anOracleTimeLiteralIsNotMistakenForAParameter() {
        // A bare colon followed by a digit is not a name, so HH:MM:SS text passes through untouched.
        NamedQuery query = NamedQuery.parse("SELECT * FROM t WHERE at > TIMESTAMP '2026-10-26 08:30:00'");

        assertThat(query.parameterNames()).isEmpty();
    }

    @Test
    void namesMayContainDigitsAndUnderscores() {
        NamedQuery query = NamedQuery.parse("SELECT * FROM t WHERE a = :order_id2");

        assertThat(query.parameterNames()).containsExactly("order_id2");
        assertThat(query.sql()).isEqualTo("SELECT * FROM t WHERE a = ?");
    }

    @Test
    void anUnterminatedLiteralIsLeftForTheDriverToComplainAbout() {
        // Guessing where the operator meant the quote to close would be worse than the driver's own
        // syntax error, which points at a position.
        NamedQuery query = NamedQuery.parse("SELECT 'oops FROM t WHERE x = :x");

        assertThat(query.sql()).isEqualTo("SELECT 'oops FROM t WHERE x = :x");
        assertThat(query.parameterNames()).isEmpty();
    }

    @Test
    void parametersAreNeverSubstitutedAsText() {
        // The injection guarantee: whatever the name, the SQL that reaches the driver has a ? there.
        NamedQuery query = NamedQuery.parse("SELECT * FROM t WHERE sku = :sku");

        assertThat(query.sql()).doesNotContain(":sku").contains("?");
    }

    @Test
    void theParameterListIsImmutable() {
        NamedQuery query = NamedQuery.parse("SELECT * FROM t WHERE a = :x");

        assertThat(query.parameterNames()).isUnmodifiable();
    }
}
