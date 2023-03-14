package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class OreSiRepository {

    @Autowired
    private BeanFactory beanFactory;

    public ApplicationRepository application() {
        return beanFactory.getBean(ApplicationRepository.class);
    }

    public RepositoryForApplication getRepository(Application application) {
        return new RepositoryForApplication(application);
    }

    public RepositoryForApplication getRepository(UUID applicationId) {
        Application application = application().findApplication(applicationId);
        return getRepository(application);
    }

    public RepositoryForApplication getRepository(String applicationNameOrId) {
        Application application = application().findApplication(applicationNameOrId);
        return getRepository(application);
    }

    public RepositoryForApplication getRepositoryAccordingRights(String applicationNameOrId) {
        Application application = application().findApplication(applicationNameOrId);
        return getRepository(application);
    }

    public class RepositoryForApplication {

        private final Application application;

        private RepositoryForApplication(Application application) {
            this.application = application;
        }

        public DataRepository data() {
            return beanFactory.getBean(DataRepository.class, application);
        }

        public ReferenceValueRepository referenceValue() {
            return beanFactory.getBean(ReferenceValueRepository.class, application);
        }

        public BinaryFileRepository binaryFile() {
            return beanFactory.getBean(BinaryFileRepository.class, application);
        }

        public AdditionalFileRepository additionalBinaryFile() {
            return beanFactory.getBean(AdditionalFileRepository.class, application);
        }

        public AuthorizationRepository authorization() {
            return beanFactory.getBean(AuthorizationRepository.class, application);
        }

        public AuthorizationReferencesRepository authorizationReferences() {
            return beanFactory.getBean(AuthorizationReferencesRepository.class, application);
        }

        public DataSynthesisRepository synthesisRepository() {
            return beanFactory.getBean(DataSynthesisRepository.class, application);
        }

        public RightsRequestRepository rightsRequestRepository() {
            return beanFactory.getBean(RightsRequestRepository.class, application);
        }
    }
}