package fr.inra.oresing.domain.file;

import fr.inra.oresing.domain.BinaryFileDataset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour FileOrUUID (sans Spring / Docker).
 */
@Tag("domain.model")
@DisplayName("FileOrUUID – tests unitaires")
class FileOrUUIDTest {

    @Test
    @DisplayName("forUUID crée un FileOrUUID avec l'ID fourni")
    void forUUID() {
        UUID id = UUID.randomUUID();
        FileOrUUID result = FileOrUUID.forUUID(id);
        assertThat(result.fileid()).isEqualTo(id);
        assertThat(result.binaryfiledataset()).isNull();
        assertThat(result.topublish()).isFalse();
    }

    @Test
    @DisplayName("from(null, UUID) crée un FileOrUUID avec un BinaryFileDataset vide")
    void fromWithNullParams() {
        UUID fileId = UUID.randomUUID();
        FileOrUUID result = FileOrUUID.from(null, fileId);
        assertThat(result.fileid()).isEqualTo(fileId);
        assertThat(result.binaryfiledataset()).isNotNull();
        assertThat(result.topublish()).isTrue(); // params == null → topublish = true
    }

    @Test
    @DisplayName("from(params, UUID) reprend le BinaryFileDataset du params")
    void fromWithParams() {
        UUID fileId = UUID.randomUUID();
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setFrom("2020-01-01T00:00:00");
        FileOrUUID params = new FileOrUUID(UUID.randomUUID(), bfd, true);
        FileOrUUID result = FileOrUUID.from(params, fileId);
        assertThat(result.fileid()).isEqualTo(fileId);
        assertThat(result.binaryfiledataset()).isEqualTo(bfd);
        assertThat(result.topublish()).isTrue();
    }

    @Test
    @DisplayName("requiredAuthorizationMatchForFile retourne true quand binaryfiledataset est null")
    void requiredAuthorizationMatchForFileWithNullDataset() {
        FileOrUUID fou = FileOrUUID.forUUID(UUID.randomUUID());
        assertThat(fou.requiredAuthorizationMatchForFile(java.util.Map.of())).isTrue();
    }

    @Test
    @DisplayName("requiredAuthorizationMatchForFile retourne true quand pas d'authzs requises")
    void requiredAuthorizationMatchForFileWithEmptyRequired() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        // pas d'autorisation requise
        FileOrUUID fou = new FileOrUUID(UUID.randomUUID(), bfd, false);
        assertThat(fou.requiredAuthorizationMatchForFile(java.util.Map.of())).isTrue();
    }
}