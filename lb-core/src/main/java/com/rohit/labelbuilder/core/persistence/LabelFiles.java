package com.rohit.labelbuilder.core.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Reads and writes {@code .lbl} template archives — the ZIP container of {@code manifest.json} +
 * {@code document.json} (+ optional {@code thumbnail.png} and {@code assets/…}), per lbl-format.md
 * §1–2. {@code document.json} is written byte-stably (see {@link DocumentJson}); the manifest carries
 * the per-save metadata and asset digests. The schema version is read first: a newer file is
 * rejected outright (§2).
 */
public final class LabelFiles {

    public static final int SCHEMA_VERSION = 1;
    public static final String MANIFEST = "manifest.json";
    public static final String DOCUMENT = "document.json";
    public static final String THUMBNAIL = "thumbnail.png";
    public static final String ASSET_PREFIX = "assets/";

    // Fixed ZIP entry timestamp so archives are reproducible (real time lives in the manifest).
    private static final long FIXED_ENTRY_TIME = 0L;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private LabelFiles() {}

    /** Serialise a package to archive bytes. Validates cross-references first (§8). */
    public static byte[] toBytes(LabelPackage pkg, SaveContext ctx) {
        Objects.requireNonNull(pkg, "pkg");
        Objects.requireNonNull(ctx, "ctx");
        LblValidator.validate(pkg);

        byte[] documentBytes = DocumentJson.writeBytes(pkg.document());
        byte[] manifestBytes = manifest(pkg, ctx);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            putEntry(zos, MANIFEST, manifestBytes);
            putEntry(zos, DOCUMENT, documentBytes);
            if (pkg.thumbnail() != null) {
                putEntry(zos, THUMBNAIL, pkg.thumbnail());
            }
            // sorted for reproducibility
            for (Map.Entry<String, byte[]> asset : new TreeMap<>(pkg.assets()).entrySet()) {
                putEntry(zos, asset.getKey(), asset.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not write .lbl archive", e);
        }
        return baos.toByteArray();
    }

    /** Parse archive bytes into a package. Rejects a newer schema and validates integrity. */
    public static LabelPackage fromBytes(byte[] archive) {
        Objects.requireNonNull(archive, "archive");
        Map<String, byte[]> entries = readEntries(archive);

        byte[] manifestBytes = entries.get(MANIFEST);
        if (manifestBytes == null) {
            throw new LabelFileException("not a .lbl archive: missing " + MANIFEST);
        }
        checkSchemaVersion(manifestBytes);

        byte[] documentBytes = entries.get(DOCUMENT);
        if (documentBytes == null) {
            throw new LabelFileException("corrupt .lbl archive: missing " + DOCUMENT);
        }

        Map<String, byte[]> assets = new LinkedHashMap<>();
        byte[] thumbnail = null;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().startsWith(ASSET_PREFIX)) {
                assets.put(e.getKey(), e.getValue());
            } else if (e.getKey().equals(THUMBNAIL)) {
                thumbnail = e.getValue();
            }
        }

        LabelPackage pkg = new LabelPackage(DocumentJson.readBytes(documentBytes), assets, thumbnail);
        LblValidator.validate(pkg);
        return pkg;
    }

    public static void save(LabelPackage pkg, Path path, SaveContext ctx) {
        try {
            Files.write(path, toBytes(pkg, ctx));
        } catch (IOException e) {
            throw new UncheckedIOException("could not save " + path, e);
        }
    }

    public static LabelPackage load(Path path) {
        try {
            return fromBytes(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + path, e);
        }
    }

    private static void checkSchemaVersion(byte[] manifestBytes) {
        int version;
        try {
            JsonNode n = MAPPER.readTree(manifestBytes);
            version = n.path("schemaVersion").asInt(-1);
        } catch (IOException e) {
            throw new LabelFileException("corrupt " + MANIFEST, e);
        }
        if (version < 1) {
            throw new LabelFileException("corrupt " + MANIFEST + ": bad schemaVersion " + version);
        }
        if (version > SCHEMA_VERSION) {
            throw new LabelFileException(
                    "created by a newer version of LabelBuilder (schema " + version + " > " + SCHEMA_VERSION + ")");
        }
        // version < SCHEMA_VERSION would run the migration chain (§7) here; none exist yet.
    }

    private static byte[] manifest(LabelPackage pkg, SaveContext ctx) {
        ObjectNode o = NODES.objectNode();
        o.put("schemaVersion", SCHEMA_VERSION);
        o.put("appVersion", ctx.appVersion());
        o.put("created", ctx.created().toString());
        o.put("modified", ctx.modified().toString());
        o.put("createdBy", ctx.user());
        o.put("modifiedBy", ctx.user());
        if (pkg.thumbnail() == null) {
            o.putNull("thumbnail");
        } else {
            o.put("thumbnail", THUMBNAIL);
        }
        ArrayNode assets = NODES.arrayNode();
        for (Map.Entry<String, byte[]> a : new TreeMap<>(pkg.assets()).entrySet()) {
            ObjectNode ao = NODES.objectNode();
            ao.put("path", a.getKey());
            ao.put("sha256", sha256(a.getValue()));
            ao.put("bytes", a.getValue().length);
            assets.add(ao);
        }
        o.set("assets", assets);
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(o);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new LabelFileException("could not serialise manifest", e);
        }
    }

    private static Map<String, byte[]> readEntries(byte[] archive) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new LabelFileException("not a readable .lbl archive", e);
        }
        if (entries.isEmpty()) {
            throw new LabelFileException("not a .lbl archive: empty or not a ZIP");
        }
        return entries;
    }

    private static void putEntry(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        entry.setTime(FIXED_ENTRY_TIME);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }

    private static String sha256(byte[] data) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** All entry names in an archive (diagnostic helper). */
    static List<String> entryNames(byte[] archive) {
        return new ArrayList<>(readEntries(archive).keySet());
    }
}
