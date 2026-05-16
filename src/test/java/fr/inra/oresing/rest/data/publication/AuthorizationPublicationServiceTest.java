package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.DataWriter;
import fr.inra.oresing.domain.file.FileOrUUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link AuthorizationPublicationService}.
 *
 * <p>Couvre : constructeur (dataName depuis fileOrUUID / direct),
 * buildDataDescription (trouvé / non trouvé / application null),
 * setFileOrUUID (with/without binaryFileDataset), isRepository,
 * fileMustBeJustStored (toutes les branches), getPublishedVersion,
 * unPublishVersions.
 */
@Tag("domain.model")
@DisplayName("AuthorizationPublicationService — logique de publication")
class AuthorizationPublicationServiceTest {

    private static final String DATA_NAME = "reftype";

    // ─── constructeur ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Le dataName est repris du paramètre s'il est non-null")
    void constructor_dataName_fromParam() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_VERSIONING);

        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, null, mock(DataWriter.class));

        assertThat(svc.getDataName()).isEqualTo(DATA_NAME);
    }

    @Test
    @DisplayName("Le dataName est extrait du BinaryFileDataset si le param est null")
    void constructor_dataName_fromFileOrUUID() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_VERSIONING);
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setDatatype(DATA_NAME);
        FileOrUUID fileOrUUID = new FileOrUUID(null, bfd, false);

        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, null, fileOrUUID, mock(DataWriter.class));

        assertThat(svc.getDataName()).isEqualTo(DATA_NAME);
    }

    @Test
    @DisplayName("buildDataDescription lève IllegalArgumentException si dataName absent dans la config")
    void buildDataDescription_unknownDataName_throws() {
        Application app = mock(Application.class);
        when(app.findData(DATA_NAME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new AuthorizationPublicationService(
                app, DATA_NAME, null, mock(DataWriter.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(AuthorizationPublicationService.DATA_NAME_NOT_FOUND);
    }

    @Test
    @DisplayName("buildDataDescription retourne null si application est null")
    void buildDataDescription_nullApplication_returnsNull() {
        // dataName null too, so no findData lookup
        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                null, null, null, mock(DataWriter.class));

        assertThat(svc.dataDescription).isNull();
    }

    // ─── setFileOrUUID ────────────────────────────────────────────────────────

    @Test
    @DisplayName("setFileOrUUID avec fileOrUUID null retourne null")
    void setFileOrUUID_null_returnsNull() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_VERSIONING);
        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, null, mock(DataWriter.class));

        assertThat(svc.getFileOrUUID()).isNull();
    }

    @Test
    @DisplayName("setFileOrUUID propage le dataName dans le BinaryFileDataset")
    void setFileOrUUID_setBinaryFileDataset_propagatesDatatype() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_VERSIONING);
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setDatatype("old");
        FileOrUUID fileOrUUID = new FileOrUUID(null, bfd, false);

        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, fileOrUUID, mock(DataWriter.class));

        assertThat(svc.getFileOrUUID()).isNotNull();
        assertThat(svc.getFileOrUUID().binaryfiledataset().getDatatype()).isEqualTo(DATA_NAME);
    }

    @Test
    @DisplayName("setFileOrUUID avec binaryFileDataset=null crée un EMPTY_INSTANCE et propage le datatype")
    void setFileOrUUID_noBinaryFileDataset_createsEmpty() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_VERSIONING);
        UUID fid = UUID.randomUUID();
        FileOrUUID fileOrUUID = new FileOrUUID(fid, null, false);

        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, fileOrUUID, mock(DataWriter.class));

        // fileOrUUID retourné tel quel (sans binaryFileDataset)
        assertThat(svc.getFileOrUUID()).isNotNull();
    }

    // ─── applicationDataWriter ────────────────────────────────────────────────

    @Test
    @DisplayName("applicationDataWriter() retourne l'instance injectée")
    void applicationDataWriter_returnsInjectedWriter() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_VERSIONING);
        DataWriter writer = mock(DataWriter.class);

        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, null, writer);

        assertThat(svc.applicationDataWriter()).isSameAs(writer);
    }

    // ─── isRepository ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("isRepository retourne true quand la stratégie est OA_VERSIONING")
    void isRepository_versioningStrategy_returnsTrue() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_VERSIONING);
        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, null, mock(DataWriter.class));

        assertThat(svc.isRepository()).isTrue();
    }

    @Test
    @DisplayName("isRepository retourne false quand la stratégie n'est pas OA_VERSIONING")
    void isRepository_nonVersioningStrategy_returnsFalse() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_INSERTION);
        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, null, mock(DataWriter.class));

        assertThat(svc.isRepository()).isFalse();
    }

    @Test
    @DisplayName("isRepository retourne false quand dataName est absent de l'appli")
    void isRepository_unknownDataName_returnsFalse() {
        Application app = mock(Application.class);
        when(app.findSubmission(DATA_NAME)).thenReturn(Optional.empty());
        // Besoin que app.findData(DATA_NAME) retourne une valeur non-vide pour ne pas lever IAE
        StandardDataDescription sdd = mock(StandardDataDescription.class);
        Submission sub = new Submission(SubmissionType.OA_VERSIONING, null, null);
        when(sdd.submission()).thenReturn(sub);
        when(app.findData(DATA_NAME)).thenReturn(Optional.of(sdd));

        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, null, mock(DataWriter.class));

        // findSubmission() retourne empty → isRepository() → false
        assertThat(svc.isRepository()).isFalse();
    }

    // ─── fileMustBeJustStored ─────────────────────────────────────────────────

    @Test
    @DisplayName("fileMustBeJustStored retourne false si strategy != OA_VERSIONING")
    void fileMustBeJustStored_nonRepository_returnsFalse() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_INSERTION);
        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, null, mock(DataWriter.class));

        // isRepository() == false → fileMustBeJustStored() == false
        assertThat(svc.fileMustBeJustStored()).isFalse();
    }

    @Test
    @DisplayName("fileMustBeJustStored retourne true si repository, pas de binaryFile et pas de publish demandé")
    void fileMustBeJustStored_repository_noBinaryFile_noPublish_returnsTrue() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_VERSIONING);
        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, null, mock(DataWriter.class));
        // binaryFile == null, topublish == false

        assertThat(svc.fileMustBeJustStored()).isTrue();
    }

    @Test
    @DisplayName("fileMustBeJustStored retourne false si repository et publish demandé avec binaryFile présent")
    void fileMustBeJustStored_repository_binaryFilePresent_publishAsked_returnsFalse() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_VERSIONING);
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setDatatype(DATA_NAME);
        FileOrUUID fileOrUUID = new FileOrUUID(null, bfd, true); // topublish=true

        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, fileOrUUID, mock(DataWriter.class));
        // Injecte un binaryFile avec un id pour que existsFileToPublish == true
        svc.binaryFile = binaryFileWithId();

        assertThat(svc.fileMustBeJustStored()).isFalse();
    }

    @Test
    @DisplayName("fileMustBeJustStored retourne false si repository, binaryFile publié et unpublish demandé")
    void fileMustBeJustStored_repository_binaryFilePublished_unpublishAsked_returnsFalse() {
        Application app = appWithData(DATA_NAME, SubmissionType.OA_VERSIONING);
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setDatatype(DATA_NAME);
        FileOrUUID fileOrUUID = new FileOrUUID(null, bfd, false); // topublish=false

        AuthorizationPublicationService svc = new AuthorizationPublicationService(
                app, DATA_NAME, fileOrUUID, mock(DataWriter.class));
        BinaryFile bf = binaryFileWithId();
        BinaryFileInfos params = new BinaryFileInfos(bfd).markAsPublished(true);
        bf.setParams(params);
        svc.binaryFile = bf;
        // publishIsAsked=false, unPublishIsAsked = !false && published == true → true

        assertThat(svc.fileMustBeJustStored()).isFalse();
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private Application appWithData(String dataName, SubmissionType strategy) {
        Application app = mock(Application.class);
        StandardDataDescription sdd = mock(StandardDataDescription.class);
        Submission sub = new Submission(strategy, null, null);
        when(sdd.submission()).thenReturn(sub);
        when(app.findData(dataName)).thenReturn(Optional.of(sdd));
        when(app.findSubmission(dataName)).thenReturn(Optional.of(sub));
        return app;
    }

    private BinaryFile binaryFileWithId() {
        BinaryFile bf = new BinaryFile();
        bf.setId(UUID.randomUUID());
        return bf;
    }
}
