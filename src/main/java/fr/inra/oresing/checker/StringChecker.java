package fr.inra.oresing.checker;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@Component
@Scope("prototype")
public class StringChecker implements Checker {

    public static final String PARAM_PATTERN = "pattern";

    private String patternString;
    private Predicate<String> predicate;

    @Override
    public void setParam(Map<String, String> params) {
        patternString = params.get(PARAM_PATTERN);
        if (patternString != null) {
            predicate = Pattern.compile(patternString).asMatchPredicate();
        }
    }

    @Override
    public String check(String value) throws CheckerException {
        if (predicate != null && !predicate.test(value)) {
            throw new CheckerException(String.format("Can't validate string '%s' with pattern '%s'", value, patternString));
        }
        return value;
    }
}
