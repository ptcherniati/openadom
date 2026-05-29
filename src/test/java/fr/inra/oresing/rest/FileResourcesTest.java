package fr.inra.oresing.rest;

import fr.inra.oresing.domain.*;
import fr.inra.oresing.rest.model.data.BinaryFileResult;
import fr.inra.oresing.rest.usecases.security.authorization.GetAllUsersUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.GetFileUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.GetFileWithDataUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.GetReferencedBinaryFilesUseCase;
import fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link FileResources}.
 *
 * <p>Couvre : getFile (présent / absent), getFileInfo (présent / absent),
 * getReferencedFiles (fichier non publié / publié), constantes HTTP.
 */
@Tag("domain.model")
@DisplayName("FileResources — contrôleur REST binaire")
class FileResourcesTest {

    private GetFileWithDataUseCase getFileWithDataUseCase;
    private GetFileUseCase getFileUseCase;
    private GetAllUsersUseCase getAllUsersUseCase;
    private GetReferencedBinaryFilesUseCase getReferencedBinaryFilesUseCase;
    private FileResources resources;

    @BeforeEach
    void setUp() {
        getFileWithDataUseCase       = mock(GetFileWithDataUseCase.class);
        getFileUseCase               = mock(GetFileUseCase.class);
        getAllUsersUseCase            = mock(GetAllUsersUseCase.class);
        getReferencedBinaryFilesUseCase = mock(GetReferencedBinaryFilesUseCase.class);

        resources = new FileResources(
                getFileWithDataUseCase,
                getFileUseCase,
                getAllUsersUseCase,
                getReferencedBinaryFilesUseCase,
                mock(PublishLifecycleService.class),
                mock(org.springframework.web.servlet.LocaleResolver.class),
                mock(fr.inra.oresing.rest.binaryFile.BinaryFileNormalizedDownloadService.class));
    }

    // ─── constantes ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Les constantes HEADER_* sont stables")
    void header_constants_are_stable() {
        assertThat(FileResources.HEADER_CONTENT_DISPOSITION).isEqualTo("Content-Disposition");
        assertThat(FileResources.HEADER_ATTACHMENT_FILENAME).contains("attachment");
    }

    // ─── getFile ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getFile retourne 200 avec le contenu si le fichier existe")
    void getFile_present_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        BinaryFile bf = new BinaryFile();
        bf.setId(id);
        bf.setName("data.csv");
        bf.setSize(5L);
        bf.setFileData(new ByteArrayInputStream("hello".getBytes()));

        when(getFileWithDataUseCase.execute("myapp", id)).thenReturn(Optional.of(bf));

        ResponseEntity<StreamingResponseBody> resp = resources.getFile("myapp", id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getFirst(FileResources.HEADER_CONTENT_DISPOSITION))
                .contains("data.csv");

        // Vérifier que le corps est streamable
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        resp.getBody().writeTo(out);
        assertThat(out.toByteArray()).isEqualTo("hello".getBytes());
    }

    @Test
    @DisplayName("getFile retourne 404 si le fichier est absent")
    void getFile_absent_returns404() {
        UUID id = UUID.randomUUID();
        when(getFileWithDataUseCase.execute("myapp", id)).thenReturn(Optional.empty());

        ResponseEntity<StreamingResponseBody> resp = resources.getFile("myapp", id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── getFileInfo ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getFileInfo retourne 404 si le fichier est absent")
    void getFileInfo_absent_returns404() {
        UUID id = UUID.randomUUID();
        when(getFileUseCase.execute("myapp", id)).thenReturn(Optional.empty());

        ResponseEntity<BinaryFileResult> resp = resources.getFileInfo("myapp", id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("getFileInfo retourne 200 avec BinaryFileResult si le fichier existe (non publié)")
    void getFileInfo_present_notPublished_returns200() {
        UUID id = UUID.randomUUID();
        BinaryFile bf = binaryFileNotPublished(id, "myapp");

        when(getFileUseCase.execute("myapp", id)).thenReturn(Optional.of(bf));
        when(getAllUsersUseCase.execute()).thenReturn(List.of());

        ResponseEntity<BinaryFileResult> resp = resources.getFileInfo("myapp", id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getBody()).isNotNull();
        // fichier non publié → aucune lookup de referencedBinaryFiles
        verifyNoInteractions(getReferencedBinaryFilesUseCase);
    }

    @Test
    @DisplayName("getFileInfo retourne 200 avec referencedBinaryFiles si le fichier est publié")
    void getFileInfo_present_published_returnsReferencedFiles() {
        UUID id = UUID.randomUUID();
        BinaryFile bf = binaryFilePublished(id, "myapp");

        OreSiUser user = userWithId(bf.getParams().createuser());

        when(getFileUseCase.execute("myapp", id)).thenReturn(Optional.of(bf));
        when(getAllUsersUseCase.execute()).thenReturn(List.of(user));
        ReferencedBinaryFiles ref = mock(ReferencedBinaryFiles.class);
        when(getReferencedBinaryFilesUseCase.execute(any(), anyString(), anySet()))
                .thenReturn(List.of(ref));

        ResponseEntity<BinaryFileResult> resp = resources.getFileInfo("myapp", id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(getReferencedBinaryFilesUseCase).execute(any(), anyString(), anySet());
    }

    // ─── getFileInfo — params null ────────────────────────────────────────────

    @Test
    @DisplayName("getFileInfo retourne 200 même si params est null (pas de NPE)")
    void getFileInfo_present_nullParams_returns200() {
        UUID id = UUID.randomUUID();
        BinaryFile bf = new BinaryFile();
        bf.setId(id);
        bf.setApplication(UUID.randomUUID());
        // params = null → published = false → pas de referencedBinaryFiles lookup

        when(getFileUseCase.execute("myapp", id)).thenReturn(Optional.of(bf));
        when(getAllUsersUseCase.execute()).thenReturn(List.of());

        ResponseEntity<BinaryFileResult> resp = resources.getFileInfo("myapp", id);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        verifyNoInteractions(getReferencedBinaryFilesUseCase);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private BinaryFile binaryFileNotPublished(UUID id, String appName) {
        BinaryFile bf = new BinaryFile();
        bf.setId(id);
        bf.setApplication(UUID.randomUUID());
        bf.setName(appName + "/data.csv");
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setDatatype("reftype");
        BinaryFileInfos params = new BinaryFileInfos(bfd);
        bf.setParams(params);
        return bf;
    }

    private BinaryFile binaryFilePublished(UUID id, String appName) {
        BinaryFile bf = new BinaryFile();
        bf.setId(id);
        bf.setApplication(UUID.randomUUID());
        bf.setName(appName + "/data.csv");
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setDatatype("reftype");
        UUID creatorId = UUID.randomUUID();
        BinaryFileInfos params = new BinaryFileInfos(
                true, null, null, creatorId, null, null, bfd);
        bf.setParams(params);
        return bf;
    }

    private OreSiUser userWithId(UUID id) {
        OreSiUser user = new OreSiUser();
        user.setId(id);
        user.setLogin("alice");
        user.setEmail("alice@example.com");
        return user;
    }
}