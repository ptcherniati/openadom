package fr.inra.oresing.rest.usecases.storage;

import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import fr.inra.oresing.rest.data.VersioningService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Component
public class UnPublishVersionBeforeDeleteUseCase {

    private final VersioningService versioningService;

    public UnPublishVersionBeforeDeleteUseCase(VersioningService versioningService) {
        this.versioningService = versioningService;
    }

    @Transactional
    public DataVersioningResult execute(Locale locale, String applicationName, UUID id, boolean delete) throws java.io.IOException {
        return versioningService.unPublishVersionBeforeDelete(locale, applicationName, id, delete);
    }
}
