package fr.inra.oresing.checker;

import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
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

        ReferenceValueRepository referenceValueRepository = repo.getRepository(application).referenceValue();
        refValues = referenceValueRepository.getReferenceIdPerKeys(refType);
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

    public String getRefType() {
        return refType;
    }
}
