package fr.inra.oresing.rest;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.ReferencedBinaryFiles;
import fr.inra.oresing.rest.model.data.BinaryFilePublicationState;
import fr.inra.oresing.rest.model.data.BinaryFileResult;
import fr.inra.oresing.rest.model.data.UserDescriptionResult;
import fr.inra.oresing.rest.usecases.security.authorization.GetAllUsersUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.GetFileUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.GetFileWithDataUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.GetReferencedBinaryFilesUseCase;
import fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.FileCopyUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
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
    private final PublishLifecycleService publishLifecycleService;
    private final LocaleResolver localeResolver;
    private final fr.inra.oresing.rest.binaryFile.BinaryFileNormalizedDownloadService normalizedDownloadService;

    public FileResources(
            GetFileWithDataUseCase getFileWithDataUseCase,
            GetFileUseCase getFileUseCase,
            GetAllUsersUseCase getAllUsersUseCase,
            GetReferencedBinaryFilesUseCase getReferencedBinaryFilesUseCase,
            PublishLifecycleService publishLifecycleService,
            LocaleResolver localeResolver,
            fr.inra.oresing.rest.binaryFile.BinaryFileNormalizedDownloadService normalizedDownloadService) {
        this.getFileWithDataUseCase = getFileWithDataUseCase;
        this.getFileUseCase = getFileUseCase;
        this.getAllUsersUseCase = getAllUsersUseCase;
        this.getReferencedBinaryFilesUseCase = getReferencedBinaryFilesUseCase;
        this.publishLifecycleService = publishLifecycleService;
        this.localeResolver = localeResolver;
        this.normalizedDownloadService = normalizedDownloadService;
    }

    @Operation(summary = "Telecharger le CSV normalise ( processed_data ) d'un binaryfile")
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{name}/file/{id}/normalized",
            produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getNormalizedFile(
            @PathVariable("name") final String name,
            @PathVariable("id") final UUID id) {
        var result = normalizedDownloadService.openNormalizedStream(name, id);
        if (!result.hasContent()) {
            // Pas de processed_data ( fichier pre-feature ou capture echouee ) .
            // 204 No Content : la ressource existe mais n'a pas de variante normalisee .
            return ResponseEntity.noContent().build();
        }
        InputStream is = result.stream();
        StreamingResponseBody body = outputStream -> {
            try (InputStream src = is) {
                FileCopyUtils.copy(src, outputStream);
            }
        };
        // Filename : metadata du binaryfile + suffixe .normalized.csv pour
        // distinguer de l'original telecharge via /file/{id} .
        String filename = getFileUseCase.execute(name, id)
                .map(BinaryFile::getName)
                .map(n -> n.replaceFirst("(?i)\\.(csv|txt)$", "") + ".normalized.csv")
                .orElse(id + ".normalized.csv");
        return ResponseEntity.ok()
                .contentLength(result.sizeBytes())
                .header(HEADER_CONTENT_DISPOSITION, HEADER_ATTACHMENT_FILENAME.formatted(filename))
                .body(body);
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

    @Operation(
            summary = "Etat de publication courant d'un binaryfile ( source de verite UI )",
            description = "Endpoint leger qui retourne uniquement l'etat published actuel"
                    + " lu directement de binaryfile.params . Utilise par le frontend"
                    + " post-terminal d'un workflow publish/unpublish/cancel pour avoir"
                    + " l'etat reel committe en BDD ( cf invariant cancel-divergence :"
                    + " workflow_log.status peut diverger en cas de race , binaryfile.published"
                    + " reste l'unique source de verite ) . Pas de cache ETag - toujours frais ."
                    + " Comparer avec /file/{id}/info qui retourne les details complets ."
    )
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{name}/file/{id}/publication-state",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BinaryFilePublicationState> getPublicationState(
            @PathVariable("name") final String name,
            @PathVariable("id") final UUID id) {
        return getFileUseCase.execute(name, id)
                .map(binaryFile -> {
                    BinaryFileInfos params = binaryFile.getParams();
                    String publishedByLogin = Optional.ofNullable(params)
                            .map(BinaryFileInfos::publisheduser)
                            .map(this::resolveUserLogin)
                            .orElse(null);
                    return ResponseEntity.ok(new BinaryFilePublicationState(
                            binaryFile.getId(),
                            params != null && params.published(),
                            params != null ? params.publisheddate() : null,
                            publishedByLogin
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Resout le login d'un utilisateur par UUID , en reutilisant
     * {@link GetAllUsersUseCase} . Retourne null si l'utilisateur a ete
     * supprime entre le moment de la publication et la requete actuelle .
     * Centralise le pattern partage avec {@link #getFileInfo} pour rester DRY .
     */
    private String resolveUserLogin(UUID userId) {
        return getAllUsersUseCase.execute().stream()
                .map(UserDescriptionResult::of)
                .filter(u -> userId.equals(u.id()))
                .map(UserDescriptionResult::login)
                .findFirst()
                .orElse(null);
    }

    @Operation(summary = "Bascule le drapeau published d'un fichier binaire ( 2-phase async )",
            description = "Endpoint de publication / depublication en 2 phases :"
                    + " phase 1 synchrone toggle le flag + recordStart workflow_log +"
                    + " envoi mail de demarrage + COMMIT . Phase 2 asynchrone ( cascade"
                    + " pipeline pour PUBLISH , DELETE SQL pour UNPUBLISH ) + recompute"
                    + " synthesis + mail de fin . Retour HTTP 202 + correlationId pour"
                    + " polling cote frontend . Voir PUBLISH_UNPUBLISH.md .")
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_WRITE')")
    @PostMapping(value = "/applications/{name}/data/{dataName}/files/{fileId}/publish",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> togglePublished(
            HttpServletRequest request,
            @PathVariable("name") final String name,
            @PathVariable("dataName") final String dataName,
            @PathVariable("fileId") final UUID fileId,
            @RequestParam("published") final boolean published) {
        java.util.Locale locale = localeResolver.resolveLocale(request);
        UUID correlationId = published
                ? publishLifecycleService.startPublish(name, fileId, locale)
                : publishLifecycleService.startUnpublish(name, fileId, locale);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "fileId",         fileId.toString(),
                "published",      published,
                "dataName",       dataName,
                "correlationId",  correlationId.toString(),
                "status",         "IN_PROGRESS"));
    }

    private List<ReferencedBinaryFiles> getReferencedFiles(BinaryFile binaryFile) {
        if (Optional.ofNullable(binaryFile.getParams())
                .stream().noneMatch(BinaryFileInfos::published)) {
            return List.of();
        }
        return getReferencedBinaryFilesUseCase.execute(
                binaryFile.getApplication(),
                binaryFile.getParams().binaryFiledataset().getDatatype(),
                Set.of(binaryFile.getId()));
    }
}