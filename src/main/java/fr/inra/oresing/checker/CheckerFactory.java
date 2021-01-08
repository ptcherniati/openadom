package fr.inra.oresing.checker;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public Checker getChecker(Configuration.ColumnDescription columnDescription, Application application) {
        if (columnDescription == null || columnDescription.getChecker() == null) {
            return getChecker("Dummy");
        }
        Configuration.CheckerDescription checkerDescription = columnDescription.getChecker();
        Checker result = getChecker(checkerDescription.getName());

        Map<String, String> params = new HashMap<>();
        if (checkerDescription.getParams() != null) {
            params.putAll(checkerDescription.getParams());
        }

        params.put(Checker.PARAM_APPLICATION, application.getId().toString());
        result.setParam(params);

        return result;
    }

    public Checker getChecker(String name) {
        Class<? extends Checker> clazz = checkers.get(name.toLowerCase());
        Checker result = applicationContext.getBean(clazz);
        return result;
    }

    public Set<ReferenceChecker> getReferenceCheckers(Application application, Configuration.DatasetDescription datasetDescription) {
        Set<ReferenceChecker> referenceCheckers = new LinkedHashSet<>();
        for (Configuration.ColumnDescription columnDescription : datasetDescription.getData().values()) {
            Checker checker = getChecker(columnDescription, application);
            if (checker instanceof ReferenceChecker) {
                ReferenceChecker referenceChecker = (ReferenceChecker) checker;
                referenceCheckers.add(referenceChecker);
            }
        }
        return referenceCheckers;
    }
}
