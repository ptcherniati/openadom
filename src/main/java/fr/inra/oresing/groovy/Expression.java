package fr.inra.oresing.groovy;

import java.util.Map;

public interface Expression<R> {

    R evaluate(Map<String, Object> context);
}
