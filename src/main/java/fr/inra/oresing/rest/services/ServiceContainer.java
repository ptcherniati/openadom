package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.services.file.BinaryFileService;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;
import fr.inra.oresing.mail.Email;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.data.VersioningService;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class ServiceContainer {

    private final ApplicationContext context;

    public ServiceContainer(ApplicationContext context) {
        this.context = context;
    }

    public ApplicationService applicationService() {
        return (ApplicationService) context.getBean("applicationService");
    }

    public DefaultAuthorizationService authorizationService() {
        return (DefaultAuthorizationService) context.getBean("authorizationService");
    }

    public AuthenticationService authenticationService() {
        return (AuthenticationService) context.getBean("authenticationService");
    }

    public DataService dataService() {
        return (DataService) context.getBean("dataService");
    }

    public VersioningService versioningService() {
        return (VersioningService) context.getBean("versioningService");
    }

    public SynthesisService synthesisService() {
        return (SynthesisService) context.getBean("synthesisService");
    }

    public BinaryFileService binaryFileService() {
        return (BinaryFileService) context.getBean("binaryFileService");
    }

    public AdditionalFileService additionalFileService() {
        return (AdditionalFileService) context.getBean("additionalFileService");
    }

    public RightsRequestService rightsRequestService() {
        return (RightsRequestService) context.getBean("rightsRequestService");
    }


    public Email emailService() {
        return (Email) context.getBean("emailService");
    }

    public fr.inra.oresing.cache.DataVersioningScopeCacheService dataVersioningScopeCacheService() {
        return context.getBean(fr.inra.oresing.cache.DataVersioningScopeCacheService.class);
    }
}