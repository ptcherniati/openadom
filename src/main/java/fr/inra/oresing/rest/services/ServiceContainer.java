package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.services.file.BinaryFileService;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;
import fr.inra.oresing.mail.Email;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.data.VersioningService;

public record ServiceContainer(
        ApplicationService applicationService,
        AuthorizationService authorizationService,
        AuthenticationService authenticationService,
        DataService dataService,
        VersioningService versioningService,
        SynthesisService synthesisService,
        BinaryFileService binaryFileService,
        AdditionalFileService additionalFileService,
        RightsRequestService rightsRequestService,
        RelationalService relationalService,
        Email emailService
) {
    public static ServiceContainer of(
            ApplicationService applicationService,
            AuthorizationService authorizationService,
            AuthenticationService authenticationService,
            DataService dataService,
            VersioningService versioningService,
            SynthesisService synthesisService,
            BinaryFileService binaryFileService,
            AdditionalFileService additionalFileService,
            RightsRequestService rightsRequestService,
            RelationalService relationalService,
            Email emailService
    ) {
        return new ServiceContainer(
                applicationService,
                authorizationService,
                authenticationService,
                dataService,
                versioningService,
                synthesisService,
                binaryFileService,
                additionalFileService,
                rightsRequestService,
                relationalService,
                emailService
        );
    }
}
