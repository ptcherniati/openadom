package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.groovy.BooleanGroovyExpression;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.DownloadDatasetQuery;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.*;

public class GroovyLineChecker implements LineChecker {

    public static final String NAME = "GroovyExpression";

    public static final String PARAM_EXPRESSION = "expression";

    public static final String PARAM_REFERENCES = "references";

    public static final String PARAM_DATATYPES = "datatypes";

    private final BooleanGroovyExpression expression;
    private Application application;
    private OreSiRepository.RepositoryForApplication repository;
    private Map<String, String> params= new HashMap<>();

    public static GroovyLineChecker forExpression(String expression, Application app, OreSiRepository.RepositoryForApplication repository, Map<String, String> params) {
        BooleanGroovyExpression groovyExpression = BooleanGroovyExpression.forExpression(expression);
        return new GroovyLineChecker(groovyExpression, app, repository, params);
    }

    public static Optional<GroovyExpression.CompilationError> validateExpression(String expression) {
        return GroovyExpression.validateExpression(expression);
    }

    private GroovyLineChecker(BooleanGroovyExpression expression, Application app, OreSiRepository.RepositoryForApplication repository, Map<String, String> params) {
        this.expression = expression;
        this.application = app;
        this.repository = repository;
        this.params = params;
    }

    @Override
    public ValidationCheckResult check(Map<VariableComponentKey, String> datum) {
        Map<String, Map<String, String>> datumAsMap = new LinkedHashMap<>();
        for (Map.Entry<VariableComponentKey, String> entry2 : datum.entrySet()) {
            String variable = entry2.getKey().getVariable();
            String component = entry2.getKey().getComponent();
            String value = entry2.getValue();
            datumAsMap.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, value);
        }
        Map<String, Object> context = buildContext(datumAsMap);
        return evaluate(context);
    }

    @Override
    public ValidationCheckResult checkReference(Map<String, String> datum) {
        Map<String, Object> context = buildContext(datum);
        return evaluate(context);
    }

    private ImmutableMap<String, Object> buildContext(Object datum) {
        Map<String, List<ReferenceValue>> references = new HashMap<>() ;
        Map<String, List<DataRow>> datatypes = new HashMap<>() ;
        Map<String, List<Map<String, String>>> referencesValues = new HashMap<>();
        Map<String, List<Map<String, Map<String, String>>>> datatypesValues = new HashMap<>();
        Optional.ofNullable(params)
                .map(p -> p.get(PARAM_REFERENCES))
                .ifPresent(refs -> {
                    Arrays.stream(refs.split(","))
                            .forEach(ref -> {
                                List<ReferenceValue> allByReferenceType = repository.referenceValue().findAllByReferenceType(ref);
                                references.put(ref, allByReferenceType);
                                allByReferenceType.stream()
                                        .map(referenceValue -> referenceValue.getRefValues())
                                        .forEach(values -> referencesValues.computeIfAbsent(ref, k->new LinkedList<>()).add(values));
                            });
                });
        Optional.ofNullable(params)
                .map(p -> p.get(PARAM_DATATYPES))
                .ifPresent(datas -> {
                    Arrays.stream(datas.split(","))
                            .forEach(dataType -> {
                                List<DataRow> allByDataType = repository.data().findAllByDataType(DownloadDatasetQuery.buildDownloadDatasetQuery(null, null, dataType, application));
                                datatypes.put(dataType, allByDataType);
                                allByDataType.stream()
                                        .map(datatValues -> datatValues.getValues())
                                        .forEach(dv -> datatypesValues.computeIfAbsent(dataType, k->new LinkedList<>()).add(dv));
                            });
                });
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        builder.put("datum",datum);
        builder.put("application",application);
        builder.put("references",references);
        builder.put("referencesValues",referencesValues);
        builder.put("datatypes",datatypes);
        builder.put("datatypesValues",datatypesValues);
        builder.put("params",Optional.ofNullable(params).orElseGet(HashMap::new));
        return builder.build();
    }

    private ValidationCheckResult evaluate(Map<String, Object> context) {
        Boolean evaluation = expression.evaluate(context);
        if (evaluation) {
            return DefaultValidationCheckResult.success();
        } else {
            return DefaultValidationCheckResult.error("checkerExpressionReturnedFalse", ImmutableMap.of("expression", expression));
        }
    }
}
