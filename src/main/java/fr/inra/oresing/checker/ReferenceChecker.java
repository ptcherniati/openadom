package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
@Scope("prototype")
public class ReferenceChecker implements Checker {

    public static final String PARAM_REFTYPE = "refType";
    public static final String PARAM_COLUMN = "column";
    public static final String PARAM_CASESENSITIVE = "caseSensitive";

    @Autowired
    private OreSiRepository repo;

    private String application;
    private String refType;
    private String column;
    private boolean caseSensitive;

    private Map<String, UUID> refValues;

    @Override
    public void setParam(Map<String, String> params) {
        application = params.get(PARAM_APPLICATION);
        refType = params.get(PARAM_REFTYPE);
        column = params.get(PARAM_COLUMN);
        caseSensitive = Boolean.parseBoolean(PARAM_CASESENSITIVE);

        refValues = loadRef(UUID.fromString(application), refType, column);
    }

    @Override
    public UUID check(String value) throws CheckerException {
        UUID result = refValues.get(normalizeCase(value));
        if (result == null) {
            throw new CheckerException(String.format("Can't find reference '%s' in %s.%s (application: %s)",
                    value, refType, StringUtils.isNotBlank(column) ? column : "", application));
        }
        return result;
    }

    protected String normalizeCase(String ref) {
        if (!caseSensitive) {
            return StringUtils.lowerCase(ref);
        }
        return ref;
    }

    private ImmutableMap<String, UUID> loadRef(UUID appId, String refType, String keyColumn) {
        Application app = repo.findApplication(appId);
        ApplicationRepository applicationRepository = repo.getRepository(app);
        return applicationRepository.getReferenceIdPerKeys(refType, keyColumn);
    }

    public String getRefType() {
        return refType;
    }

    public String getColumn() {
        return column;
    }

    public boolean isCaseSensitive() {
        return caseSensitive;
    }
}
