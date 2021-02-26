package fr.inra.oresing.checker;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Scope("prototype")
public class FloatChecker implements Checker {

    public static final String PARAM_DEFAULT = "default";

    private String defaultValue;

    @Override
    public void setParam(Map<String, String> params) {
        defaultValue = params.get(PARAM_DEFAULT);
    }

    @Override
    public Float check(String value) throws CheckerException {
        try {
            if (StringUtils.isBlank(value) && defaultValue != null) {
                value = defaultValue;
            }
            return Float.parseFloat(value);
        } catch (Exception eee) {
            throw new CheckerException(String.format("Can't parse float '%s'", value), eee);
        }

    }
}
