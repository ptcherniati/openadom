package fr.inra.oresing.checker;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Scope("prototype")
public class IntegerChecker implements Checker {

    public static final String PARAM_DEFAULT = "default";

    private String defaultValue;

    @Override
    public void setParam(Map<String, String> params) {
        defaultValue = params.get(PARAM_DEFAULT);
    }

    @Override
    public Integer check(String value) throws CheckerException {
        try {
            if (StringUtils.isBlank(value) && defaultValue != null) {
                value = defaultValue;
            }
            return Integer.parseInt(value);
        } catch (Exception eee) {
            throw new CheckerException(String.format("Can't parse integer '%s'", value), eee);
        }

    }
}
