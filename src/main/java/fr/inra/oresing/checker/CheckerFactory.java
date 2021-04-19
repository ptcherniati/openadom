package fr.inra.oresing.checker;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.VariableComponentKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private Checker getChecker(Configuration.ColumnDescription columnDescription, Application application, String component) {
        if (columnDescription == null || columnDescription.getComponents().get(component) == null) {
            return getChecker("Dummy");
        }
        Configuration.CheckerDescription checkerDescription = columnDescription.getComponents().get(component).getChecker();
        Checker result = getChecker(checkerDescription.getName());

        ImmutableMap<String, String> params = ImmutableMap.<String, String>builder()
                .putAll(checkerDescription.getParams())
                .put(Checker.PARAM_APPLICATION, application.getId().toString())
                .build();
        result.setParam(params);

        return result;
    }

    private Checker getChecker(String name) {
        Class<? extends Checker> clazz = checkers.get(name.toLowerCase());
        Checker result = applicationContext.getBean(clazz);
        return result;
    }

    public ImmutableSet<ReferenceChecker> getReferenceCheckers(Application application, String dataType) {
        ImmutableSet<ReferenceChecker> referenceCheckers = getCheckers(application, dataType).values().stream()
                .filter(checker -> checker instanceof ReferenceChecker)
                .map(checker -> (ReferenceChecker) checker)
                .collect(ImmutableSet.toImmutableSet());
        return referenceCheckers;
    }

    public ImmutableMap<VariableComponentKey, Checker> getCheckers(Application app, String dataType) {
        Preconditions.checkArgument(app.getConfiguration().getDataTypes().containsKey(dataType), "Pas de type de données " + dataType + " dans " + app);
        Configuration.DataTypeDescription dataTypeDescription = app.getConfiguration().getDataTypes().get(dataType);
        Map<VariableComponentKey, Checker> checkers = new LinkedHashMap<>();
        for (Map.Entry<String, Configuration.ColumnDescription> variableEntry : dataTypeDescription.getData().entrySet()) {
            String variable = variableEntry.getKey();
            Configuration.ColumnDescription variableDescription = variableEntry.getValue();
            for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : variableDescription.getComponents().entrySet()) {
                String component = componentEntry.getKey();
                VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
                checkers.put(variableComponentKey, getChecker(variableDescription, app, component));
            }
        }
        return ImmutableMap.copyOf(checkers);
    }
}
