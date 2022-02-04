package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.transformer.TransformationConfiguration;
import fr.inra.oresing.groovy.BooleanGroovyExpression;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.model.SomethingThatCanProvideEvaluationContext;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.DefaultValidationCheckResult;
import fr.inra.oresing.rest.DownloadDatasetQuery;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class GroovyLineChecker implements LineChecker<GroovyLineCheckerConfiguration> {

    public static final String NAME = "GroovyExpression";

    private final BooleanGroovyExpression expression;
    private final Application application;
    private final OreSiRepository.RepositoryForApplication repository;
    private final GroovyLineCheckerConfiguration configuration;

    private GroovyLineChecker(BooleanGroovyExpression expression, Application app, OreSiRepository.RepositoryForApplication repository, GroovyLineCheckerConfiguration configuration) {
        this.expression = expression;
        this.application = app;
        this.repository = repository;
        this.configuration = configuration;
    }

    public static GroovyLineChecker forExpression(String expression, Application app, OreSiRepository.RepositoryForApplication repository, GroovyLineCheckerConfiguration configuration) {
        BooleanGroovyExpression groovyExpression = BooleanGroovyExpression.forExpression(expression);
        return new GroovyLineChecker(groovyExpression, app, repository, configuration);
    }

    public static Optional<GroovyExpression.CompilationError> validateExpression(String expression) {
        return GroovyExpression.validateExpression(expression);
    }

    public static ImmutableMap<String, Object> buildContext(SomethingThatCanProvideEvaluationContext datum, Application application, TransformationConfiguration params, OreSiRepository.RepositoryForApplication repository) {
        Optional<String> configurationReferences = Optional.of(params)
                .map(TransformationConfiguration::getReferences);
        Optional<String> configurationDataTypes = Optional.empty();
        ImmutableMap<String, Object> context = buildContext(datum, application, repository, configurationReferences, configurationDataTypes);
        return context;
    }

    public static ImmutableMap<String, Object> buildContext(Datum datum, Application application, Configuration.VariableComponentDescriptionConfiguration params, OreSiRepository.RepositoryForApplication repository) {
        Optional<String> configurationReferences = Optional.of(params)
                .map(Configuration.VariableComponentDescriptionConfiguration::getReferences);
        Optional<String> configurationDataTypes = Optional.empty();
        ImmutableMap<String, Object> context = buildContext(datum, application, repository, configurationReferences, configurationDataTypes);
        return context;
    }

    public static ImmutableMap<String, Object> buildContext(SomethingThatCanProvideEvaluationContext datum, Application application, GroovyLineCheckerConfiguration params, OreSiRepository.RepositoryForApplication repository) {
        Optional<String> configurationReferences = Optional.of(params)
                .map(GroovyLineCheckerConfiguration::getReferences);
        Optional<String> configurationDataTypes = Optional.of(params)
                .map(GroovyLineCheckerConfiguration::getDatatypes);
        ImmutableMap<String, Object> context = buildContext(datum, application, repository, configurationReferences, configurationDataTypes);
        return context;
    }

    private static ImmutableMap<String, Object> buildContext(SomethingThatCanProvideEvaluationContext datum, Application application, OreSiRepository.RepositoryForApplication repository, Optional<String> configurationReferences, Optional<String> configurationDataTypes) {
        Map<String, List<ReferenceValue>> references = new HashMap<>();
        Map<String, List<DataRow>> datatypes = new HashMap<>();
        Map<String, List<Map<String, String>>> referencesValues = new HashMap<>();
        Map<String, List<Map<String, Map<String, String>>>> datatypesValues = new HashMap<>();
        configurationReferences
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

        configurationDataTypes
                .ifPresent(datas -> {
                    Arrays.stream(datas.split(","))
                            .forEach(dataType -> {
                                List<DataRow> allByDataType = repository.data().findAllByDataType(DownloadDatasetQuery.buildDownloadDatasetQuery(null, null, dataType, application));
                                datatypes.put(dataType, allByDataType);
                                allByDataType.stream()
                                        .map(datatValues -> datatValues.getValues())
                                        .forEach(dv -> datatypesValues.computeIfAbsent(dataType, k -> new LinkedList<>()).add(dv));
                            });
                });
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        builder.putAll(datum.getEvaluationContext());
        builder.put("application", application);
        builder.put("references", references);
        builder.put("referencesValues", referencesValues);
        builder.put("datatypes", datatypes);
        builder.put("datatypesValues", datatypesValues);
        return builder.build();
    }

    @Override
    public GroovyLineCheckerConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    public ValidationCheckResult check(Datum datum) {
        Map<String, Object> context = buildContext(datum, application, configuration, repository);
        return evaluate(context);
    }

    @Override
    public ValidationCheckResult checkReference(ReferenceDatum datum) {
        Map<String, Object> context = buildContext(datum, application, configuration, repository);
        return evaluate(context);
    }

    private ValidationCheckResult evaluate(Map<String, Object> context) {
        Boolean evaluation = expression.evaluate(context);
        if (evaluation) {
            return DefaultValidationCheckResult.success();
        } else {
            return DefaultValidationCheckResult.error(
                    "checkerExpressionReturnedFalse",
                    ImmutableMap.of("expression", expression));
        }
    }
}