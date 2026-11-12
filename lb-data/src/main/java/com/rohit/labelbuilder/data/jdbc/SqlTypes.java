package com.rohit.labelbuilder.data.jdbc;

import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.OptionalInt;

/**
 * Maps JDBC column types onto {@link FieldType}, and JDBC values onto ones a label can render.
 *
 * <p>Unlike a file, a database column has a declared type and a declared length, so nothing is guessed
 * here — {@code TypeInference} has no job on this path.
 *
 * <p>Two mappings are deliberate rather than mechanical:
 *
 * <ul>
 *   <li><b>{@code NUMERIC}/{@code DECIMAL} stay {@link java.math.BigDecimal}.</b> Its scale is part of
 *       the value, so a {@code DECIMAL(10,2)} holding 1.50 renders "1.50". Converting to double would
 *       print "1.5" and drop the pence off a price label.
 *   <li><b>Dates and times become {@code java.time} values.</b> {@code java.sql.Timestamp.toString()}
 *       produces {@code 2026-10-26 08:30:00.0} — a trailing tenth of a second nobody asked for, and
 *       not the ISO form the expression engine will parse in Phase 12.
 * </ul>
 */
public final class SqlTypes {

    private SqlTypes() {}

    public static FieldType of(int jdbcType) {
        return switch (jdbcType) {
            case Types.BIT, Types.BOOLEAN -> FieldType.BOOLEAN;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> FieldType.INTEGER;
            case Types.DECIMAL, Types.NUMERIC, Types.FLOAT, Types.REAL, Types.DOUBLE -> FieldType.DECIMAL;
            case Types.DATE -> FieldType.DATE;
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> FieldType.TIME;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> FieldType.TIMESTAMP;
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> FieldType.BINARY;
            // CHAR/VARCHAR/CLOB and anything a driver reports that we do not model: text is the type
            // that can hold it, and the value still renders as the driver's own string form.
            default -> FieldType.STRING;
        };
    }

    /** One column's definition, read from the metadata the driver already has. */
    public static FieldDefinition field(ResultSetMetaData metaData, int column) throws SQLException {
        String name = label(metaData, column);
        FieldType type = of(metaData.getColumnType(column));
        OptionalInt length = type == FieldType.STRING && metaData.getPrecision(column) > 0
                ? OptionalInt.of(metaData.getPrecision(column))
                : OptionalInt.empty();
        OptionalInt scale = type == FieldType.DECIMAL && metaData.getScale(column) > 0
                ? OptionalInt.of(metaData.getScale(column))
                : OptionalInt.empty();
        boolean nullable = metaData.isNullable(column) != ResultSetMetaData.columnNoNulls;
        return new FieldDefinition(name, type, length, scale, nullable);
    }

    /**
     * The name to bind to: the column's alias where the query gave one, else its name.
     *
     * <p>An alias is what the operator wrote, so {@code SELECT price AS Preis} must bind as
     * {@code Preis}. Some drivers return an empty label for a computed column; a positional name is
     * better than a field nobody can reference.
     */
    static String label(ResultSetMetaData metaData, int column) throws SQLException {
        String label = metaData.getColumnLabel(column);
        if (label == null || label.isBlank()) {
            label = metaData.getColumnName(column);
        }
        return label == null || label.isBlank() ? "Column" + column : label;
    }

    /** Converts a value the driver returned into one that renders and compares predictably. */
    public static Object value(Object raw) {
        return switch (raw) {
            case null -> null;
            case java.sql.Date date -> date.toLocalDate();
            case java.sql.Time time -> time.toLocalTime();
            case java.sql.Timestamp timestamp -> timestamp.toLocalDateTime();
            // Everything else passes through: a driver that already returns java.time, or a String,
            // Number, BigDecimal or byte[], is giving us the value in its best form already.
            default -> raw;
        };
    }
}
