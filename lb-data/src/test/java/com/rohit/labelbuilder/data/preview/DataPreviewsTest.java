package com.rohit.labelbuilder.data.preview;

import static org.assertj.core.api.Assertions.assertThat;

import com.rohit.labelbuilder.data.DataException;
import com.rohit.labelbuilder.data.DataRecord;
import com.rohit.labelbuilder.data.DataSource;
import com.rohit.labelbuilder.data.FieldDefinition;
import com.rohit.labelbuilder.data.FieldType;
import com.rohit.labelbuilder.data.InMemoryDataSource;
import com.rohit.labelbuilder.data.MapRecord;
import com.rohit.labelbuilder.data.RecordSet;
import com.rohit.labelbuilder.data.file.CsvDataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** One preview serves every source, which is the dividend from making DataSource one interface. */
class DataPreviewsTest {

    @TempDir
    Path folder;

    private static final List<FieldDefinition> FIELDS =
            List.of(FieldDefinition.text("Code", 20), FieldDefinition.of("Qty", FieldType.INTEGER));

    private static InMemoryDataSource rows(int count) {
        List<DataRecord> records = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            records.add(MapRecord.of("Code", "A" + i, "Qty", i));
        }
        return new InMemoryDataSource("Product", FIELDS, records);
    }

    @Test
    void aSmallSourceIsShownWhole() {
        DataPreview preview = DataPreviews.of(rows(3));

        assertThat(preview.isSuccessful()).isTrue();
        assertThat(preview.truncated()).isFalse();
        assertThat(preview.rows()).hasSize(3);
        assertThat(preview.text(0, "Code")).isEqualTo("A1");
        assertThat(preview.fields()).extracting(FieldDefinition::name).containsExactly("Code", "Qty");
    }

    @Test
    void aLargeSourceIsCutOffAndSaysSo() {
        // Previewing a production table must not read forty million rows.
        DataPreview preview = DataPreviews.of(rows(5_000), Map.of(), 10);

        assertThat(preview.rows()).hasSize(10);
        assertThat(preview.truncated()).isTrue();
        assertThat(preview.summary())
                .isEqualTo("first 10 rows in " + preview.elapsed().toMillis() + " ms");
    }

    @Test
    void exactlyTheLimitIsNotCalledTruncated() {
        assertThat(DataPreviews.of(rows(10), Map.of(), 10).truncated()).isFalse();
        assertThat(DataPreviews.of(rows(11), Map.of(), 10).truncated()).isTrue();
    }

    @Test
    void anEmptySourceIsDistinguishedFromAFailedOne() {
        DataPreview preview = DataPreviews.of(rows(0));

        assertThat(preview.isSuccessful()).isTrue();
        assertThat(preview.isEmpty()).isTrue();
        assertThat(preview.summary()).isEqualTo("no rows matched");
        // The columns are still there, so a label can be laid out against it.
        assertThat(preview.fields()).hasSize(2);
    }

    @Test
    void aFileSourcePreviewsThroughTheSameCode() throws IOException {
        Path file = folder.resolve("p.csv");
        Files.writeString(file, "Code,Name\nA1,Bolt\nA2,Nut\n", StandardCharsets.UTF_8);

        DataPreview preview = DataPreviews.of(new CsvDataSource("Product", file));

        assertThat(preview.rows()).hasSize(2);
        assertThat(preview.text(1, "Name")).isEqualTo("Nut");
    }

    @Test
    void aMissingFileIsAMessageNotAnException() {
        DataPreview preview = DataPreviews.of(new CsvDataSource("Product", folder.resolve("absent.csv")));

        assertThat(preview.isSuccessful()).isFalse();
        assertThat(preview.error()).isPresent();
        assertThat(preview.summary()).isNotBlank();
    }

    @Test
    void aSourceThatFailsWithNoMessageStillCaptionsThePane() {
        // A blank pane captioned with nothing would look like a hang.
        DataPreview preview = DataPreviews.of(new DataSource() {
            @Override
            public String name() {
                return "Broken";
            }

            @Override
            public List<FieldDefinition> fields() {
                throw new IllegalStateException();
            }

            @Override
            public RecordSet open(Map<String, Object> parameters) {
                throw new UnsupportedOperationException();
            }
        });

        assertThat(preview.isSuccessful()).isFalse();
        assertThat(preview.error()).contains("IllegalStateException");
    }

    @Test
    void theCursorIsClosedEvenWhenThePreviewStopsEarly() {
        // The preview stops after its limit; the source must still be released rather than left open
        // while the operator reads the result.
        boolean[] closed = {false};
        DataSource source = new DataSource() {
            @Override
            public String name() {
                return "Counting";
            }

            @Override
            public List<FieldDefinition> fields() {
                return FIELDS;
            }

            @Override
            public RecordSet open(Map<String, Object> parameters) {
                return new RecordSet() {
                    private int served;

                    @Override
                    public List<FieldDefinition> fields() {
                        return FIELDS;
                    }

                    @Override
                    public boolean next() {
                        return ++served < 1_000_000;
                    }

                    @Override
                    public DataRecord current() {
                        return MapRecord.of("Code", "A" + served, "Qty", served);
                    }

                    @Override
                    public OptionalLong size() {
                        return OptionalLong.empty();
                    }

                    @Override
                    public void close() {
                        closed[0] = true;
                    }
                };
            }
        };

        DataPreview preview = DataPreviews.of(source, Map.of(), 5);

        assertThat(preview.rows()).hasSize(5);
        assertThat(preview.truncated()).isTrue();
        assertThat(closed[0]).as("the cursor was closed").isTrue();
    }

    @Test
    void parametersArePassedThroughToTheSource() {
        DataSource echoing = new DataSource() {
            @Override
            public String name() {
                return "Echo";
            }

            @Override
            public List<FieldDefinition> fields() {
                return List.of(FieldDefinition.text("Value", 20));
            }

            @Override
            public RecordSet open(Map<String, Object> parameters) {
                if (!parameters.containsKey("sku")) {
                    throw new DataException("no value for 'sku'");
                }
                return new InMemoryDataSource(
                                "Echo", fields(), List.of(MapRecord.of("Value", String.valueOf(parameters.get("sku")))))
                        .open();
            }
        };

        assertThat(DataPreviews.of(echoing).isSuccessful()).isFalse();
        assertThat(DataPreviews.of(echoing, Map.of("sku", "A1")).text(0, "Value"))
                .isEqualTo("A1");
    }
}
