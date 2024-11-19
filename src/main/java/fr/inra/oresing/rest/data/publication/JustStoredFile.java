package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public record JustStoredFile(AuthorizationPublicationService builder) implements State {
}
