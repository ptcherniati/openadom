package fr.inra.oresing.rest;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.ReferencedBinaryFiles;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.rest.model.data.BinaryFileResult;
import fr.inra.oresing.rest.model.data.UserDescriptionResult;
import fr.inra.oresing.rest.usecases.security.authorization.GetAllUsersUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.GetFileUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.GetFileWithDataUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.GetReferencedBinaryFilesUseCase;
import fr.inra.oresing.rest.usecases.storage.versioning.PublishToggleUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "Bearer Authentication")
public class FileResources {

    private final GetFileWithDataUseCase getFileWithDataUseCase;
    private final GetFileUseCase getFileUseCase;
    private final GetAllUsersUseCase getAllUsersUseCase;
    private final GetReferencedBinaryFilesUseCase getReferencedBinaryFilesUseCase;
    private final PublishToggleUseCase publishToggleUseCase;

    public FileResources(
            GetFileWithDataUseCase getFileWithDataUseCase,
            GetFileUseCase getFileUseCase,
            GetAllUsersUseCase getAllUsersUseCase,
            GetReferencedBinaryFilesUseCase getReferencedBinaryFilesUseCase,
            PublishToggleUseCase publishToggleUseCase) {
        this.getFileWithDataUseCase = getFileWithDataUseCase;
        this.getFileUseCase = getFileUseCase;
        this.getAllUsersUseCase = getAllUsersUseCase;
        this.getReferencedBinaryFilesUseCase = getReferencedBinaryFilesUseCase;
        this.publishToggleUseCase = publishToggleUseCase;
    }

    public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    public static final String HEADER_ATTACHMENT_FILENAME = "attachment;filename=%1$s";

    @Operation(summary = "Télécharger le contenu binaire d'un fichier")
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{name}/file/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getFile(
            @PathVariable("name") final String name,
            @PathVariable("id") final UUID id) {
        final Optional<BinaryFile> optionalBinaryFile = getFileWithDataUseCase.execute(name, id);
        if (optionalBinaryFile.isPresent()) {
            final BinaryFile binaryFile = optionalBinaryFile.get();

            StreamingResponseBody body;
            String filename;
            try (InputStream inputStream = binaryFile.getFileData()) {
                filename = binaryFile.getName();
                body = outputStream -> FileCopyUtils.copy(inputStream, outputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return ResponseEntity.ok()
                    .contentLength(binaryFile.getSize())
                    .header(HEADER_CONTENT_DISPOSITION, HEADER_ATTACHMENT_FILENAME.formatted(filename))
                    .body(body);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(summary = "Récupérer les métadonnées d'un fichier (sans le contenu binaire)")
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{name}/file/{id}/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BinaryFileResult> getFileInfo(
            @PathVariable("name") final String name,
            @PathVariable("id") final UUID id) {
        return getFileUseCase.execute(name, id)
                .map(binaryFile -> {
                    Map<UUID, UserDescriptionResult> users = getAllUsersUseCase.execute()
                            .stream()
                            .map(UserDescriptionResult::of)
                            .collect(Collectors.toMap(UserDescriptionResult::id, Function.identity()));
                    UserDescriptionResult createUser = Optional.ofNullable(binaryFile.getParams())
                            .map(BinaryFileInfos::createuser)
                            .map(users::get)
                            .orElse(null);
                    UserDescriptionResult publishedUser = Optional.ofNullable(binaryFile.getParams())
                            .map(BinaryFileInfos::publisheduser)
                            .map(users::get)
                            .orElse(null);
                    List<ReferencedBinaryFiles> referencedFiles = getReferencedFiles(binaryFile);
                    return ResponseEntity.ok(BinaryFileResult.of(binaryFile, createUser, publishedUser, referencedFiles));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Bascule le drapeau published d'un fichier binaire (action cascade 1-item)",
            description = "Toggle dedie pour publier ou depublier un fichier deja stocke , execute"
                    + " comme un workflow cascade Action Pattern ( Sources.single + Sinks.action ) ."
                    + " Court-circuite le chemin lourd VersioningService.createData mais conserve"
                    + " l'audit log workflow_log et la visibilite dashboard.")
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_WRITE')")
    @PostMapping(value = "/applications/{name}/data/{dataName}/files/{fileId}/publish",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> togglePublished(
            @PathVariable("name") final String name,
            @PathVariable("dataName") final String dataName,
            @PathVariable("fileId") final UUID fileId,
            @RequestParam("published") final boolean published) {
        UUID affected = publishToggleUseCase.execute(name, fileId, published);
        return ResponseEntity.ok(Map.of(
                "fileId",    affected.toString(),
                "published", published,
                "dataName",  dataName));
    }

    private List<ReferencedBinaryFiles> getReferencedFiles(BinaryFile binaryFile) {
        if (Optional.ofNullable(binaryFile.getParams())
                .stream().noneMatch(BinaryFileInfos::published)) {
            return null;
        }
        return getReferencedBinaryFilesUseCase.execute(
                binaryFile.getApplication(),
                binaryFile.getParams().binaryFiledataset().getDatatype(),
                Set.of(binaryFile.getId()));
    }
}