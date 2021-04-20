package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ApplicationRepository {

    @Autowired
    private BeanFactory beanFactory;

    private final Application application;

    public ApplicationRepository(Application application) {
        this.application = application;
    }

    public DataDao data() {
        return beanFactory.getBean(DataDao.class, application);
    }

    public ReferenceValueDao referenceValue() {
        return beanFactory.getBean(ReferenceValueDao.class, application);
    }

    public BinaryFileDao binaryFile() {
        return beanFactory.getBean(BinaryFileDao.class, application);
    }
}
