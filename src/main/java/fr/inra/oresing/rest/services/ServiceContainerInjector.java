package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.services.file.BinaryFileService;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;
import fr.inra.oresing.mail.Email;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.data.VersioningService;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ServiceContainerInjector implements ApplicationContextAware, PriorityOrdered {

    private ConfigurableListableBeanFactory services;

    public ServiceContainer getServiceContainer() {
        return serviceContainer;
    }

    private ServiceContainer serviceContainer;


    private void injectServiceContainer() {
        Map<String, Object> serviceContainerBeans = Arrays.asList(services.getBeanNamesForType(ServiceContainerBean.class)).stream()
                .collect(Collectors.toMap(
                        Function.identity(),
                        services::getBean
                ));
        serviceContainer = new ServiceContainer(
                (ApplicationService) serviceContainerBeans.get("applicationService"),
                (AuthorizationService) serviceContainerBeans.get("authorizationService"),
                (AuthenticationService) serviceContainerBeans.get("authenticationService"),
                (DataService) serviceContainerBeans.get("dataService"),
                (VersioningService) serviceContainerBeans.get("versioningService"),
                (SynthesisService) serviceContainerBeans.get("synthesisService"),
                (BinaryFileService) serviceContainerBeans.get("binaryFileService"),
                (AdditionalFileService) serviceContainerBeans.get("additionalFileService"),
                (RightsRequestService) serviceContainerBeans.get("rightsRequestService"),
                (RelationalService) serviceContainerBeans.get("relationalService"),
                (Email) serviceContainerBeans.get("emailService")
        );

        serviceContainerBeans.values().forEach(service -> {
            if (service instanceof ServiceContainerBean) {
                ((ServiceContainerBean) service).setServiceContainer(serviceContainer);
            }
        });
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.services = ((ConfigurableApplicationContext) applicationContext).getBeanFactory();
        injectServiceContainer();

    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
