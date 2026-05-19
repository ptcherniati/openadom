package fr.inra.oresing.domain.data.read.ouput;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests unitaires purs de {@link KeepAliveZipOutputStream} — aucun contexte Spring.
 * Vérifie que le flux ZIP produit est valide et que close() termine proprement le timer.
 */
@Tag("domain.model")
@DisplayName("KeepAliveZipOutputStream — flux ZIP valide et close()")
class KeepAliveZipOutputStreamTest {

    @Test
    @DisplayName("constructeur crée un flux sans erreur")
    void constructorCreatesStreamWithoutError() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (KeepAliveZipOutputStream zip = new KeepAliveZipOutputStream(baos)) {
            assertThat(zip).isNotNull();
        }
        assertThat(baos.size()).isPositive();
    }

    @Test
    @DisplayName("write(int) et close() produisent un ZIP valide")
    void writeIntProducesValidZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (KeepAliveZipOutputStream zip = new KeepAliveZipOutputStream(baos)) {
            zip.putNextEntry(new ZipEntry("test.txt"));
            zip.write('A');
            zip.closeEntry();
        }
        // Verify the ZIP is valid by reading it back
        byte[] zipBytes = baos.toByteArray();
        try (ZipInputStream zis = new ZipInputStream(
                new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            assertThat(entry.getName()).isEqualTo("test.txt");
        }
    }

    @Test
    @DisplayName("write(byte[]) et close() produisent un ZIP valide")
    void writeByteArrayProducesValidZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] content = "hello world".getBytes(StandardCharsets.UTF_8);
        try (KeepAliveZipOutputStream zip = new KeepAliveZipOutputStream(baos)) {
            zip.putNextEntry(new ZipEntry("hello.txt"));
            zip.write(content);
            zip.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            byte[] read = zis.readAllBytes();
            assertThat(new String(read, StandardCharsets.UTF_8)).isEqualTo("hello world");
        }
    }

    @Test
    @DisplayName("write(byte[], int, int) et close() produisent un ZIP valide")
    void writeByteArrayWithOffsetProducesValidZip() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] content = "XXhello worldXX".getBytes(StandardCharsets.UTF_8);
        try (KeepAliveZipOutputStream zip = new KeepAliveZipOutputStream(baos)) {
            zip.putNextEntry(new ZipEntry("partial.txt"));
            zip.write(content, 2, 11); // "hello world"
            zip.closeEntry();
        }
        byte[] zipBytes = baos.toByteArray();
        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry = zis.getNextEntry();
            assertThat(entry).isNotNull();
            byte[] read = zis.readAllBytes();
            assertThat(new String(read, StandardCharsets.UTF_8)).isEqualTo("hello world");
        }
    }

    @Test
    @DisplayName("close() peut être appelé plusieurs fois sans exception")
    void closeIsIdempotent() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        KeepAliveZipOutputStream zip = new KeepAliveZipOutputStream(baos);
        zip.close();
        // Second close is safe (timer is already cancelled)
        assertDoesNotThrow(zip::close);
    }
}