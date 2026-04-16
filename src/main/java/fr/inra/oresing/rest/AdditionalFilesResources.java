package fr.inra.oresing.rest;

import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.rest.model.additionalfiles.CreateAdditionalFileRequest;
import fr.inra.oresing.rest.model.rightsrequest.GetAdditionalFilesResult;
import fr.inra.oresing.rest.usecases.storage.additionalfile.CreateOrUpdateAdditionalFileUseCase;
import fr.inra.oresing.rest.usecases.storage.additionalfile.DeleteAdditionalFilesUseCase;
import fr.inra.oresing.rest.usecases.storage.additionalfile.FindAdditionalFileUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "Bearer Authentication")
public class AdditionalFilesResources {

    private final FindAdditionalFileUseCase findAdditionalFileUseCase;
    private final DeleteAdditionalFilesUseCase deleteAdditionalFilesUseCase;
    private final CreateOrUpdateAdditionalFileUseCase createOrUpdateAdditionalFileUseCase;

    public AdditionalFilesResources(
            FindAdditionalFileUseCase findAdditionalFileUseCase,
            DeleteAdditionalFilesUseCase deleteAdditionalFilesUseCase,
            CreateOrUpdateAdditionalFileUseCase createOrUpdateAdditionalFileUseCase) {
        this.findAdditionalFileUseCase = findAdditionalFileUseCase;
        this.deleteAdditionalFilesUseCase = deleteAdditionalFilesUseCase;
        this.createOrUpdateAdditionalFileUseCase = createOrUpdateAdditionalFileUseCase;
    }

    public static final String LIST_DELIMITER = ",";

    @GetMapping(value = "/applications/{nameOrId}/additionalFiles/{additionalFileName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAdditionalFilesResult> listAdditionalFilesNames(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("additionalFileName") final String additionalFileName,
            @JsonParam(required = false) AdditionalFilesInfos additionalFilesInfos) {
        if (additionalFilesInfos == null) {
            additionalFilesInfos = new AdditionalFilesInfos();
        }
        additionalFilesInfos.setFiletype(additionalFilesInfos.getFiletype() == null 
            ? additionalFileName : additionalFilesInfos.getFiletype());
        final GetAdditionalFilesResult list = findAdditionalFileUseCase.execute(nameOrId, additionalFilesInfos);
        return ResponseEntity.ok(list);
    }

    @DeleteMapping(value = "/applications/{nameOrId}/additionalFiles", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(description = "Delete a additionalFiles", summary = "Delete additional file based on params search")
    public ResponseEntity<String> removeAdditionalFiles(
            @PathVariable("nameOrId") final String nameOrId,
            @JsonParam(value = "params", required = false) final AdditionalFilesInfos additionalFilesInfos) {
        final List<UUID> deletedFiles = deleteAdditionalFilesUseCase.execute(nameOrId, additionalFilesInfos);
        if (deletedFiles != null && !deletedFiles.isEmpty()) {
            return ResponseEntity.ok(deletedFiles.stream().map(UUID::toString).collect(Collectors.joining(LIST_DELIMITER)));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/applications/{nameOrId}/additionalFiles/{additionalFileName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UUID> createAdditionalFile(@PathVariable("nameOrId") final String nameOrId,
                                                     @PathVariable("additionalFileName") final String additionalFileName,
                                                     @RequestParam(value = "file", required = false) final MultipartFile file,
                                                     @JsonParam(value = "params") final CreateAdditionalFileRequest createAdditionalFileRequest) {
        final UUID fileUUID = createOrUpdateAdditionalFileUseCase.execute(createAdditionalFileRequest, additionalFileName, nameOrId, file);
        return ResponseEntity.ok(fileUUID);
    }
}