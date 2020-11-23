package fr.inra.oresing.checker;

import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.persistence.OreSiRepository;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

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

    protected Map<String, UUID> loadRef(UUID appId, String refType, String column) {
        List<ReferenceValue> list = repo.findReference(appId, refType);
        Map<String, UUID> result;
        if (StringUtils.isNotBlank(column)) {
            result = list.stream()
                    .collect(Collectors.toMap(v -> normalizeCase(v.getRefValues().get(column)), ReferenceValue::getId, (s, a) -> s));
        } else {
            result = list.stream()
                    .flatMap(v -> v.getRefValues().values().stream().map(value -> Pair.of(normalizeCase(value), v.getId())))
                    .collect(Collectors.toMap(Pair::getLeft, Pair::getRight, (s, a) -> s));
        }
        return result;
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
