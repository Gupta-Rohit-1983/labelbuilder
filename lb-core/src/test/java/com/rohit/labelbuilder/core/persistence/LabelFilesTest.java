package com.rohit.labelbuilder.core.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LabelFilesTest {

    private final LabelPackage pkg = Fixtures.richPackage();
    private final SaveContext ctx = SaveContext.fixed("0.1.0", "rohit", Instant.parse("2026-08-31T10:00:00Z"));

    @Test
    void archiveRoundTripsDocumentAssetsAndThumbnail() {
        LabelPackage back = LabelFiles.fromBytes(LabelFiles.toBytes(pkg, ctx));

        assertThat(back.document()).isEqualTo(pkg.document());
        assertThat(back.assets().get(Fixtures.LOGO))
                .containsExactly(pkg.assets().get(Fixtures.LOGO));
        assertThat(back.thumbnail()).containsExactly(pkg.thumbnail());
    }

    @Test
    void entriesAreInDeterministicOrder() {
        byte[] archive = LabelFiles.toBytes(pkg, ctx);
        assertThat(LabelFiles.entryNames(archive))
                .containsExactly("manifest.json", "document.json", "thumbnail.png", Fixtures.LOGO);
    }

    @Test
    void manifestRecordsSchemaVersionAndAssetDigest() {
        String manifest = manifestOf(LabelFiles.toBytes(pkg, ctx));
        assertThat(manifest).contains("\"schemaVersion\" : 1");
        assertThat(manifest).contains("\"path\" : \"" + Fixtures.LOGO + "\"");
        assertThat(manifest).contains("sha256");
    }

    @Test
    void savesAndLoadsFromDisk(@TempDir Path dir) {
        Path file = dir.resolve("carton.lbl");
        LabelFiles.save(pkg, file, ctx);

        LabelPackage back = LabelFiles.load(file);
        assertThat(back.document()).isEqualTo(pkg.document());
    }

    @Test
    void aNewerSchemaIsRejected() {
        byte[] futuristic = archive(2, DocumentJson.writeBytes(pkg.document()));
        assertThatThrownBy(() -> LabelFiles.fromBytes(futuristic))
                .isInstanceOf(LabelFileException.class)
                .hasMessageContaining("newer version");
    }

    @Test
    void missingDocumentIsRejected() {
        // an archive with only a manifest, no document.json
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write("{\"schemaVersion\":1}".getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertThatThrownBy(() -> LabelFiles.fromBytes(baos.toByteArray()))
                .isInstanceOf(LabelFileException.class)
                .hasMessageContaining("document.json");
    }

    @Test
    void garbageBytesAreRejected() {
        assertThatThrownBy(() -> LabelFiles.fromBytes("this is not a zip".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(LabelFileException.class);
    }

    // --- helpers ---

    private static String manifestOf(byte[] archive) {
        // manifest is the first entry; read it back through the same reader path via a fresh parse
        return new String(entry(archive, "manifest.json"), StandardCharsets.UTF_8);
    }

    private static byte[] entry(byte[] archive, String name) {
        try (var zis = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.getName().equals(name)) {
                    return zis.readAllBytes();
                }
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        throw new AssertionError("no entry " + name);
    }

    private static byte[] archive(int schemaVersion, byte[] documentJson) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            zos.putNextEntry(new ZipEntry("manifest.json"));
            zos.write(("{\"schemaVersion\":" + schemaVersion + "}").getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("document.json"));
            zos.write(documentJson);
            zos.closeEntry();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return baos.toByteArray();
    }
}
