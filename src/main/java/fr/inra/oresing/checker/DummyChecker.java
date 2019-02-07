package fr.inra.oresing.checker;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Check nothing
 */
@Component
public class DummyChecker implements Checker {

    @Override
    public void setParam(Map<String, String> params) {
    }

    @Override
    public String check(String value) throws CheckerException {
        return value;
    }
}
