package fr.inra.oresing.checker;

import org.apache.commons.lang3.time.DateParser;
import org.apache.commons.lang3.time.FastDateFormat;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;

@Component
@Scope("prototype")
public class DateChecker implements Checker {

    public static final String PARAM_PATTERN = "pattern";

    private DateParser dateParser;

    private String pattern;

    @Override
    public void setParam(Map<String, String> params) {
        pattern = params.get(PARAM_PATTERN);
        dateParser = FastDateFormat.getInstance(pattern);
    }

    @Override
    public Date check(String value) throws CheckerException {
        try {
            Date date = dateParser.parse(value);
            return date;
        } catch (Exception eee) {
            throw new CheckerException(String.format("Can't parse date '%s' with pattern '%s'", value, dateParser.getPattern()), eee);
        }

    }

    public String getPattern() {
        return pattern;
    }
}
