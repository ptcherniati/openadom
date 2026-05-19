package fr.inra.oresing.domain.additionalfiles;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
@Tag("domain.model")
@DisplayName("AdditionalFiles domain classes")
class AdditionalFilesDomainTest {
    // ---- AdditionalBinaryFile ----
    @Test
    @DisplayName("AdditionalBinaryFile - getters/setters")
    void additionalBinaryFile_gettersSetters() {
        AdditionalBinaryFile f = new AdditionalBinaryFile();
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        f.setApplication(appId);
        f.setFileType("pdf");
        f.setFileName("test.pdf");
        f.setComment("a comment");
        f.setSize(1024L);
        f.setData(new byte[]{1, 2, 3});
        f.setCreationUser(userId);
        f.setUpdateUser(userId);
        f.setFileInfos(Map.of("key", "val"));
        f.setForApplication(true);
        assertThat(f.getApplication()).isEqualTo(appId);
        assertThat(f.getFileType()).isEqualTo("pdf");
        assertThat(f.getFileName()).isEqualTo("test.pdf");
        assertThat(f.getComment()).isEqualTo("a comment");
        assertThat(f.getSize()).isEqualTo(1024L);
        assertThat(f.getData()).containsExactly(1, 2, 3);
        assertThat(f.getCreationUser()).isEqualTo(userId);
        assertThat(f.getUpdateUser()).isEqualTo(userId);
        assertThat(f.getFileInfos()).containsEntry("key", "val");
        assertThat(f.isForApplication()).isTrue();
    }
    @Test
    @DisplayName("AdditionalBinaryFile - toString ne leve pas d exception")
    void additionalBinaryFile_toString() {
        AdditionalBinaryFile f = new AdditionalBinaryFile();
        f.setFileName("test.pdf");
        assertThat(f.toString()).isNotBlank();
    }
    // ---- AdditionalFilesInfos ----
    @Test
    @DisplayName("AdditionalFilesInfos - valeurs par defaut")
    void additionalFilesInfos_defaults() {
        AdditionalFilesInfos info = new AdditionalFilesInfos();
        assertThat(info.getUuids()).isEmpty();
        assertThat(info.getFileNames()).isEmpty();
        assertThat(info.getAuthorizations()).isEmpty();
        assertThat(info.getInfosByFileName()).isEmpty();
        assertThat(info.getLocale()).isNotBlank();
        assertThat(info.getOffset()).isEqualTo(0L);
        assertThat(info.getLimit()).isNull();
    }
    @Test
    @DisplayName("AdditionalFilesInfos - getters/setters")
    void additionalFilesInfos_gettersSetters() {
        AdditionalFilesInfos info = new AdditionalFilesInfos();
        UUID id = UUID.randomUUID();
        info.setUuids(Set.of(id));
        info.setFileNames(Set.of("file.csv"));
        info.setFiletype("csv");
        info.setLocale("en_US");
        info.setOffset(5L);
        info.setLimit(100L);
        assertThat(info.getUuids()).contains(id);
        assertThat(info.getFileNames()).contains("file.csv");
        assertThat(info.getFiletype()).isEqualTo("csv");
        assertThat(info.getLocale()).isEqualTo("en_US");
        assertThat(info.getOffset()).isEqualTo(5L);
        assertThat(info.getLimit()).isEqualTo(100L);
    }
    @Test
    @DisplayName("AdditionalFilesInfos.FieldFilters - getFilter retourne null si filter est null")
    void fieldFilters_getFilterNull() {
        AdditionalFilesInfos.FieldFilters ff = new AdditionalFilesInfos.FieldFilters();
        assertThat(ff.getFilter()).isNull();
    }
    @Test
    @DisplayName("AdditionalFilesInfos.FieldFilters - getFilter retourne la valeur si non null")
    void fieldFilters_getFilterValue() {
        AdditionalFilesInfos.FieldFilters ff = new AdditionalFilesInfos.FieldFilters();
        ff.setFilter("myFilter");
        assertThat(ff.getFilter()).isEqualTo("myFilter");
    }
    @Test
    @DisplayName("AdditionalFilesInfos.FieldFilters - isRegExp defaut false")
    void fieldFilters_isRegExpDefault() {
        AdditionalFilesInfos.FieldFilters ff = new AdditionalFilesInfos.FieldFilters();
        assertThat(ff.isRegExp).isFalse();
    }
    @Test
    @DisplayName("AdditionalFilesInfos.IntervalValues - champs publics")
    void intervalValues_publicFields() {
        AdditionalFilesInfos.IntervalValues iv = new AdditionalFilesInfos.IntervalValues();
        iv.from = "2020-01-01";
        iv.to = "2020-12-31";
        assertThat(iv.from).isEqualTo("2020-01-01");
        assertThat(iv.to).isEqualTo("2020-12-31");
    }
    // ---- AuthorizationsAdditionalFileResult ----
    @Test
    @DisplayName("AuthorizationsAdditionalFileResult - record accesseurs")
    void authorizationsAdditionalFileResult_accessors() {
        Map<OperationAdditionalFileType, List<String>> authMap = Map.of(
                OperationAdditionalFileType.depot, List.of("user1")
        );
        AuthorizationsAdditionalFileResult r = new AuthorizationsAdditionalFileResult(authMap, "appName");
        assertThat(r.authorizationResults()).containsKey(OperationAdditionalFileType.depot);
        assertThat(r.applicationName()).isEqualTo("appName");
    }
    // ---- AuthorizationsAdditionalFilesResult ----
    @Test
    @DisplayName("AuthorizationsAdditionalFilesResult - record accesseurs")
    void authorizationsAdditionalFilesResult_accessors() {
        Map<OperationAdditionalFileType, List<String>> authMap = Map.of(
                OperationAdditionalFileType.extraction, List.of("admin")
        );
        AuthorizationsAdditionalFilesResult r = new AuthorizationsAdditionalFilesResult(authMap, "app2", true);
        assertThat(r.authorizationResults()).containsKey(OperationAdditionalFileType.extraction);
        assertThat(r.applicationName()).isEqualTo("app2");
        assertThat(r.isAdministrator()).isTrue();
    }
    // ---- OreSiAdditionalFileAuthorization ----
    @Test
    @DisplayName("OreSiAdditionalFileAuthorization - getters/setters")
    void oreSiAdditionalFileAuthorization_gettersSetters() {
        OreSiAdditionalFileAuthorization auth = new OreSiAdditionalFileAuthorization();
        UUID appId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        auth.setName("authName");
        auth.setOreSiUsers(Set.of(userId));
        auth.setApplication(appId);
        auth.setAdditionalFiles(Map.of(OperationAdditionalFileType.admin, List.of("file1")));
        assertThat(auth.getName()).isEqualTo("authName");
        assertThat(auth.getOreSiUsers()).contains(userId);
        assertThat(auth.getApplication()).isEqualTo(appId);
        assertThat(auth.getAdditionalFiles()).containsKey(OperationAdditionalFileType.admin);
    }
    @Test
    @DisplayName("OreSiAdditionalFileAuthorization - toString ne leve pas d exception")
    void oreSiAdditionalFileAuthorization_toString() {
        OreSiAdditionalFileAuthorization auth = new OreSiAdditionalFileAuthorization();
        auth.setName("test");
        assertThat(auth.toString()).isNotBlank();
    }
}
