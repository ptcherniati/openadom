package fr.inra.oresing.checker;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Scope("prototype")
public class FloatChecker implements Checker {

    @Override
    public void setParam(Map<String, String> params) {

    }

    @Override
    public Float check(String value) throws CheckerException {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException eee) {
            throw new CheckerException(String.format("Can't parse float '%s'", value), eee);
        }
    }
}
