package fr.inra.oresing.checker;

import fr.inra.oresing.model.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class CheckerFactory {

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * key: le nom du checker en lower case
     * value: le checker
     */
    private Map<String, Class<? extends Checker>> checkers;

    public CheckerFactory(List<? extends Checker> checkers) {
        this.checkers = checkers.stream().collect(Collectors.toMap(c -> c.getName().toLowerCase(), c -> c.getClass()));
    }

    public Checker getChecker(Configuration.CheckerDescription desc, UUID applicationId) {
        Checker result = getChecker(desc.getName());

        Map<String, String> params = new HashMap<>();
        if (desc.getParams() != null) {
            params.putAll(desc.getParams());
        }

        params.put(Checker.PARAM_APPLICATION, applicationId.toString());
        result.setParam(params);

        return result;
    }

    public Checker getChecker(String name) {
        Class<? extends Checker> clazz = checkers.get(name.toLowerCase());
        Checker result = applicationContext.getBean(clazz);
        return result;
    }

}
