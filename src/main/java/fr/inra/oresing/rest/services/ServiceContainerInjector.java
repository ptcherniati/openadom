package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.services.file.BinaryFileService;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;
import fr.inra.oresing.mail.Email;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.data.VersioningService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ServiceContainerInjector implements BeanPostProcessor, ApplicationListener<ContextRefreshedEvent> {

    private final Map<String, Object> services = new HashMap<>();
    private ServiceContainer serviceContainer;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof ServiceContainerBean) {
            services.put(beanName, bean);
        }
        return bean;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        injectServiceContainer();
    }

    private void injectServiceContainer() {
        serviceContainer = new ServiceContainer(
                (ApplicationService) services.get("applicationService"),
                (AuthorizationService) services.get("authorizationService"),
                (AuthenticationService) services.get("authenticationService"),
                (DataService) services.get("dataService"),
                (VersioningService) services.get("versioningService"),
                (SynthesisService) services.get("synthesisService"),
                (BinaryFileService) services.get("binaryFileService"),
                (AdditionalFileService) services.get("additionalFileService"),
                (RightsRequestService) services.get("rightsRequestService"),
                (RelationalService) services.get("relationalService"),
                (Email) services.get("emailService")
        );

        services.values().forEach(service -> {
            if (service instanceof ServiceContainerBean) {
                ((ServiceContainerBean) service).setServiceContainer(serviceContainer);
            }
        });
    }
}
