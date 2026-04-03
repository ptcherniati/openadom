package fr.inra.oresing.rest.usecases.storage;

import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.rest.data.VersioningService;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Locale;

@Component
public class CreateDataUseCase {
    private final VersioningService versioningService;

    public CreateDataUseCase(VersioningService versioningService) {
        this.versioningService = versioningService;
    }

    @Transactional
    public DataVersioningResult execute(
            Locale locale,
            String nameOrId,
            String dataName,
            DataFile file,
            boolean beforeDelete,
            boolean withEmail) throws IOException {
        return versioningService.createData(locale, nameOrId, dataName, file, beforeDelete, withEmail);
    }
}
