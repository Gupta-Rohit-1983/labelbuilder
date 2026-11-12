package com.rohit.labelbuilder.data.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JdbcDialectTest {

    @Test
    void buildsUrlsFromHostPortAndDatabase() {
        assertThat(JdbcDialect.SQL_SERVER.url("db01", 0, "Warehouse"))
                .startsWith("jdbc:sqlserver://db01:1433;databaseName=Warehouse");
        assertThat(JdbcDialect.MYSQL.url("db01", 0, "warehouse")).isEqualTo("jdbc:mysql://db01:3306/warehouse");
        assertThat(JdbcDialect.POSTGRESQL.url("db01", 5555, "warehouse"))
                .isEqualTo("jdbc:postgresql://db01:5555/warehouse");
        assertThat(JdbcDialect.ORACLE.url("db01", 0, "ORCL")).isEqualTo("jdbc:oracle:thin:@//db01:1521/ORCL");
    }

    @Test
    void aFileBasedDialectIgnoresHostAndPort() {
        assertThat(JdbcDialect.SQLITE.isNetworked()).isFalse();
        assertThat(JdbcDialect.SQLITE.url(null, 0, "C:/data/stock.db")).isEqualTo("jdbc:sqlite:C:/data/stock.db");
    }

    @Test
    void aPastedUrlIsRecognised() {
        assertThat(JdbcDialect.ofUrl("jdbc:sqlserver://db01:1433;databaseName=W"))
                .isEqualTo(JdbcDialect.SQL_SERVER);
        assertThat(JdbcDialect.ofUrl("jdbc:mysql://db01/warehouse")).isEqualTo(JdbcDialect.MYSQL);
        assertThat(JdbcDialect.ofUrl("jdbc:postgresql://db01/warehouse")).isEqualTo(JdbcDialect.POSTGRESQL);
        assertThat(JdbcDialect.ofUrl("jdbc:oracle:thin:@//db01:1521/ORCL")).isEqualTo(JdbcDialect.ORACLE);
        assertThat(JdbcDialect.ofUrl("jdbc:sqlite:stock.db")).isEqualTo(JdbcDialect.SQLITE);
        assertThat(JdbcDialect.ofUrl("jdbc:h2:mem:test")).isEqualTo(JdbcDialect.H2);
    }

    @Test
    void anUnknownUrlFallsBackToGenericRatherThanGuessing() {
        assertThat(JdbcDialect.ofUrl("jdbc:db2://db01:50000/W")).isEqualTo(JdbcDialect.GENERIC);
        assertThat(JdbcDialect.ofUrl(null)).isEqualTo(JdbcDialect.GENERIC);
    }

    @Test
    void mysqlAsksForTheOneFetchSizeThatActuallyStreams() {
        // Connector/J reads the whole result set into the client for any other value, which would make
        // the cursor contract a fiction (risk R-06).
        assertThat(JdbcDialect.MYSQL.streamingFetchSize()).isEqualTo(Integer.MIN_VALUE);
    }

    @Test
    void everyOtherDialectUsesABoundedFetchSize() {
        for (JdbcDialect dialect : JdbcDialect.values()) {
            if (dialect != JdbcDialect.MYSQL) {
                assertThat(dialect.streamingFetchSize())
                        .as("fetch size of %s", dialect)
                        .isGreaterThan(0);
            }
        }
    }

    @Test
    void theDriversThatShipWithTheProductAreOnTheClasspath() {
        assertThat(JdbcDialect.SQL_SERVER.isDriverAvailable()).isTrue();
        assertThat(JdbcDialect.MYSQL.isDriverAvailable()).isTrue();
        assertThat(JdbcDialect.H2.isDriverAvailable()).isTrue();
    }

    @Test
    void aDriverThatHasToBeSuppliedByTheUserReportsItsAbsence() {
        // Oracle's driver is not redistributable, so FR-DS-05 has the user add the JAR. If that ever
        // changes, this assertion is the reminder to revisit the wizard's messaging.
        assertThat(JdbcDialect.ORACLE.isDriverAvailable()).isFalse();
    }

    @Test
    void aHandTypedUrlNeedsNoDriverClassOfItsOwn() {
        assertThat(JdbcDialect.GENERIC.driverClassName()).isNull();
        assertThat(JdbcDialect.GENERIC.isDriverAvailable()).isTrue();
        assertThat(JdbcDialect.GENERIC.url(null, 0, "jdbc:whatever:thing")).isEqualTo("jdbc:whatever:thing");
    }

    @Test
    void everyDialectHasSomethingToShowInAPicker() {
        for (JdbcDialect dialect : JdbcDialect.values()) {
            assertThat(dialect.displayName()).as("display name of %s", dialect).isNotBlank();
        }
    }
}
