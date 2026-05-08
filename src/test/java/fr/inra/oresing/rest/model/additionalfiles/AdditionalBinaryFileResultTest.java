package fr.inra.oresing.rest.model.additionalfiles;

import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du DTO AdditionalBinaryFileResult — sans contexte Spring.
 */
@Tag("domain.model")
@DisplayName("AdditionalBinaryFileResult")
class AdditionalBinaryFileResultTest {

    private static AdditionalBinaryFile buildBinaryFile() {
        AdditionalBinaryFile f = new AdditionalBinaryFile();
        f.setApplication(UUID.randomUUID());
        f.setCreationUser(UUID.randomUUID());
        f.setUpdateUser(UUID.randomUUID());
        f.setFileType("application/pdf");
        f.setFileName("report.pdf");
        f.setComment("test comment");
        f.setSize(1024L);
        f.setFileInfos(Map.of("key1", "val1"));
        f.setForApplication(true);
        return f;
    }

    @Test
    @DisplayName("constructeur mappe tous les champs depuis AdditionalBinaryFile")
    void constructorMapsFields() {
        AdditionalBinaryFile file = buildBinaryFile();
        Map<String, List<AuthorizationParsed>> associates = Map.of();
        AdditionalBinaryFileResult result = new AdditionalBinaryFileResult(file, associates);
        assertThat(result.getApplication()).isEqualTo(file.getApplication());
        assertThat(result.getUser()).isEqualTo(file.getCreationUser());
        assertThat(result.getUpdateUser()).isEqualTo(file.getUpdateUser());
        assertThat(result.getFileType()).isEqualTo("application/pdf");
        assertThat(result.getFileName()).isEqualTo("report.pdf");
        assertThat(result.getComment()).isEqualTo("test comment");
        assertThat(result.getSize()).isEqualTo(1024L);
        assertThat(result.getAdditionalBinaryFileForm()).containsEntry("key1", "val1");
        assertThat(result.getAssociates()).isEmpty();
        assertThat(result.getForApplication()).isTrue();
    }

    @Test
    @DisplayName("additionalBinaryFileType est le même que fileType")
    void additionalBinaryFileTypeEqualsFileType() {
        AdditionalBinaryFile file = buildBinaryFile();
        AdditionalBinaryFileResult result = new AdditionalBinaryFileResult(file, Map.of());
        assertThat(result.getAdditionalBinaryFileType()).isEqualTo(result.getFileType());
    }

    @Test
    @DisplayName("associates est correctement mappé")
    void associatesAreMapped() {
        AdditionalBinaryFile file = buildBinaryFile();
        AuthorizationParsed ap = new AuthorizationParsed(null, null, null, null);
        Map<String, List<AuthorizationParsed>> associates = Map.of("scope", List.of(ap));
        AdditionalBinaryFileResult result = new AdditionalBinaryFileResult(file, associates);
        assertThat(result.getAssociates()).containsKey("scope");
    }
}
