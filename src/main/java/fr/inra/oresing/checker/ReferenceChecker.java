package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Scope("prototype")
public class ReferenceChecker implements Checker {

    public static final String PARAM_REFTYPE = "refType";

    @Autowired
    private OreSiRepository repo;

    private String application;
    private String refType;

    private Map<String, UUID> refValues;

    @Override
    public void setParam(Map<String, String> params) {
        application = params.get(PARAM_APPLICATION);
        refType = params.get(PARAM_REFTYPE);

        refValues = loadRef(UUID.fromString(application), refType);
    }

    @Override
    public UUID check(String value) throws CheckerException {
        UUID result = refValues.get(value);
        if (result == null) {
            throw new CheckerException(String.format("Il n'existe pas d'élément ayant pour clé '%s' dans le référentiel %s (application: %s). Clés connues : " + refValues.keySet(),
                    value, refType, application));
        }
        return result;
    }

    private ImmutableMap<String, UUID> loadRef(UUID appId, String refType) {
        Application app = repo.findApplication(appId);
        ApplicationRepository applicationRepository = repo.getRepository(app);
        return applicationRepository.referenceValue().getReferenceIdPerKeys(refType);
    }

    public String getRefType() {
        return refType;
    }
}
