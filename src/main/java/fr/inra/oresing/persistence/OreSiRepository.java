package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.application.Application;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OreSiRepository {

    @Autowired
    private BeanFactory beanFactory;

    public ApplicationRepository application() {
        return beanFactory.getBean(ApplicationRepository.class);
    }

    public RepositoryForApplication getRepository(final Application application) {
        return new RepositoryForApplication(application);
    }

    public RepositoryForApplication getRepository(final String applicationNameOrId) {
        final Application application = application().findApplication(applicationNameOrId);
        return getRepository(application);
    }

    public RepositoryForApplication getRepositoryAccordingRights(final String applicationNameOrId) {
        final Application application = application().findApplication(applicationNameOrId);
        return getRepository(application);
    }
    public class RepositoryForApplication {

        private final Application application;

        private RepositoryForApplication(final Application application) {
            super();
            this.application = application;
        }

        public DataRepository data() {
            return beanFactory.getBean(DataRepository.class, application);
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

        public AuthorizationAdditionalFilesRepository authorizationAdditionalFiles() {
            return beanFactory.getBean(AuthorizationAdditionalFilesRepository.class, application);
        }

        public DataSynthesisRepository synthesisRepository() {
            return beanFactory.getBean(DataSynthesisRepository.class, application);
        }

        public RightsRequestRepository rightsRequestRepository() {
            return beanFactory.getBean(RightsRequestRepository.class, application);
        }
    }
}