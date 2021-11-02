package fr.inra.oresing.checker;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.model.internationalization.InternationalizationDisplay;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import fr.inra.oresing.rest.ApplicationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class CheckerFactory {

    public static final String COLUMNS = "columns";
    public static final String VARIABLE_COMPONENT_KEY = "variableComponentKey";
    public static final String REQUIRED = "required";
    @Autowired
    private OreSiRepository repository;

    public ImmutableMap<VariableComponentKey, ReferenceLineChecker> getReferenceLineCheckers(Application app, String dataType) {
        return getLineCheckers(app, dataType).stream()
                .filter(lineChecker -> lineChecker instanceof ReferenceLineChecker)
                .map(lineChecker -> (ReferenceLineChecker) lineChecker)
                .collect(ImmutableMap.toImmutableMap(rlc -> (VariableComponentKey) rlc.getTarget().getTarget(), Function.identity()));
    }

    public ImmutableSet<LineChecker> getReferenceValidationLineCheckers(Application app, String reference) {
        Preconditions.checkArgument(app.getConfiguration().getReferences().containsKey(reference), "Pas de référence " + reference + " dans " + app);
        Configuration.ReferenceDescription referenceDescription = app.getConfiguration().getReferences().get(reference);
        ImmutableSet.Builder<LineChecker> checkersBuilder = ImmutableSet.builder();
        addCheckersFromLineValidationDescriptions(app, referenceDescription.getValidations(), checkersBuilder, Type.REFERENCE.getParam()); //Configuration.DataTypeDescription dataTypeDescription,
        ImmutableSet<LineChecker> lineCheckers = checkersBuilder.build();
        if (log.isTraceEnabled()) {
            log.trace("pour " + app.getName() + ", " + reference + ", on validera avec " + lineCheckers);
        }
        return lineCheckers;
    }

    public ImmutableSet<LineChecker> getLineCheckers(Application app, String dataType) {
        return getLineCheckers(app, dataType, null);
    }

    public ImmutableSet<LineChecker> getLineCheckers(Application app, String dataType, String locale) {
        Preconditions.checkArgument(app.getConfiguration().getDataTypes().containsKey(dataType), "Pas de type de données " + dataType + " dans " + app);
        Configuration.DataTypeDescription dataTypeDescription = app.getConfiguration().getDataTypes().get(dataType);
        ImmutableSet.Builder<LineChecker> checkersBuilder = ImmutableSet.builder();
        for (Map.Entry<String, Configuration.ColumnDescription> variableEntry : dataTypeDescription.getData().entrySet()) {
            String variable = variableEntry.getKey();
            Configuration.ColumnDescription variableDescription = variableEntry.getValue();
            for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : variableDescription.getComponents().entrySet()) {
                parseVariableComponentdescription(app, dataType, locale, checkersBuilder, variable, variableDescription, componentEntry);
            }
        }
        addCheckersFromLineValidationDescriptions(app, dataTypeDescription.getValidations(), checkersBuilder, Type.DATATYPE.getParam()); //Configuration.DataTypeDescription dataTypeDescription,
        ImmutableSet<LineChecker> lineCheckers = checkersBuilder.build();
        if (log.isTraceEnabled()) {
            log.trace("pour " + app.getName() + ", " + dataType + ", on validera avec " + lineCheckers);
        }
        return lineCheckers;
    }

    private void parseVariableComponentdescription(Application app, String dataType, String locale, ImmutableSet.Builder<LineChecker> checkersBuilder, String variable, Configuration.ColumnDescription variableDescription, Map.Entry<String, Configuration.VariableComponentDescription> componentEntry) {
        String component = componentEntry.getKey();
        VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
        if (variableDescription.getComponents().get(component) == null) {
            if (log.isDebugEnabled()) {
                log.debug("pas de règle de validation pour " + variableComponentKey);
            }
        } else {
            Configuration.CheckerDescription checkerDescription = variableDescription.getComponents().get(component).getChecker();
            CheckerOnOneVariableComponentLineChecker variableComponentChecker;
            CheckerTarget checkerTarget = CheckerTarget.getInstance(variableComponentKey);
            if ("Reference".equals(checkerDescription.getName())) {
                variableComponentChecker = getCheckerOnReferenceChecker(app, dataType, locale, checkerDescription, checkerTarget);
            } else if ("Date".equals(checkerDescription.getName())) {
                String pattern = checkerDescription.getParams().get(DateLineChecker.PARAM_PATTERN);
                variableComponentChecker = new DateLineChecker(checkerTarget, pattern, checkerDescription.getParams());
            } else if ("Integer".equals(checkerDescription.getName())) {
                variableComponentChecker = new IntegerChecker(checkerTarget, checkerDescription.getParams());
            } else if ("Float".equals(checkerDescription.getName())) {
                variableComponentChecker = new FloatChecker(checkerTarget, checkerDescription.getParams());
            } else if ("RegularExpression".equals(checkerDescription.getName())) {
                String pattern = checkerDescription.getParams().get(RegularExpressionChecker.PARAM_PATTERN);
                variableComponentChecker = new RegularExpressionChecker(checkerTarget, pattern, checkerDescription.getParams());
            } else {
                throw new IllegalArgumentException("checker inconnu " + checkerDescription.getName());
            }
            Preconditions.checkState(variableComponentChecker.getTarget().getTarget().equals(variableComponentKey));
            checkersBuilder.add(variableComponentChecker);
        }
    }

    private CheckerOnOneVariableComponentLineChecker getCheckerOnReferenceChecker(Application app, String dataType, String locale, Configuration.CheckerDescription checkerDescription, CheckerTarget checkerTarget) {
        CheckerOnOneVariableComponentLineChecker variableComponentChecker;
        String refType = checkerDescription.getParams().get(ReferenceLineChecker.PARAM_REFTYPE);
        ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
        ImmutableMap<String, UUID> referenceValues;
        ImmutableMap<String, String> display = null;
        if (locale == null) {
            referenceValues = referenceValueRepository.getReferenceIdPerKeys(refType);
        } else {
            ImmutableMap<String, ApplicationResult.Reference.ReferenceUUIDAndDisplay> referenceIdAndDisplayPerKeys = referenceValueRepository.getReferenceIdAndDisplayPerKeys(refType, locale);
            referenceValues = ImmutableMap.copyOf(
                    referenceIdAndDisplayPerKeys.entrySet().stream()
                            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getUuid()))
            );
            display = ImmutableMap.copyOf(
                    referenceIdAndDisplayPerKeys.entrySet().stream()
                            .collect(Collectors.toMap(e -> e.getKey(), e -> getOrBuildDisplay(app, refType, dataType, locale, e)))
            );
        }
        variableComponentChecker = new ReferenceLineChecker(checkerTarget, refType, referenceValues, display, checkerDescription.getParams());
        return variableComponentChecker;
    }

    private String getOrBuildDisplay(Application app, String refType, String dataType, String locale, Map.Entry<String, ApplicationResult.Reference.ReferenceUUIDAndDisplay> e) {
        Optional<String> patternOpt = Optional.ofNullable(app)
                .map(a -> app.getConfiguration())
                .map(configuration -> configuration.getInternationalization())
                .map(internationalizationMap -> internationalizationMap.getDataTypes())
                .map(internationalizationDataTypeMap -> internationalizationDataTypeMap.get(dataType))
                .map(internationalizationDataTypeMap -> internationalizationDataTypeMap.getInternationalizationDisplay())
                .map(internationalizationDisplayMap -> internationalizationDisplayMap.get(refType))
                .map(internationalizationDisplay1 -> internationalizationDisplay1.getPattern())
                .map(patternMap -> patternMap.get(locale));
        if(patternOpt.isPresent()){
           String  pattern = patternOpt.get();
            for (String column : InternationalizationDisplay.getPatternColumns(pattern)) {
                pattern = pattern.replaceAll("\\{"+column+"\\}",e.getValue().getValues().get(column));
            }
            return pattern;
        }
        return e.getValue().getDisplay() == null ? e.getKey() : e.getValue().getDisplay();
    }

    private void addCheckersFromLineValidationDescriptions(Application app, LinkedHashMap<String, Configuration.LineValidationRuleDescription> lineValidationDescriptions, ImmutableSet.Builder<LineChecker> checkersBuilder, String param) {
        for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationEntry : lineValidationDescriptions.entrySet()) {
            Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationEntry.getValue();
            Configuration.CheckerDescription checkerDescription = lineValidationRuleDescription.getChecker();
            LineChecker lineChecker;
            Map<String, String> params = checkerDescription.getParams();
            String pattern;
            if (GroovyLineChecker.NAME.equals(checkerDescription.getName())) {
                String expression = params.get(GroovyLineChecker.PARAM_EXPRESSION);
                lineChecker = GroovyLineChecker.forExpression(expression, app, repository.getRepository(app), params);
                checkersBuilder.add(lineChecker);
            } else {
                List<CheckerTarget> checkerTargets = buildCheckerTarget(params);
                if (checkerTargets != null) {
                    checkerTargets.forEach(checkerTarget -> buildCheckers(app, checkerDescription, params, checkerTarget, checkersBuilder));
                } else {
                    throw new IllegalArgumentException(String.format("Pour le checker de ligne %s, le paramètre %s doit être fourni.", checkerDescription.getName(), param));
                }
            }
            checkersBuilder.build();
        }
    }

    private void buildCheckers(Application app, Configuration.CheckerDescription checkerDescription, Map<String, String> params, CheckerTarget target, ImmutableSet.Builder<LineChecker> checkersBuilder) {
        String pattern;
        switch (checkerDescription.getName()) {
            case "Date":
                pattern = params.get(DateLineChecker.PARAM_PATTERN);
                checkersBuilder.add(new DateLineChecker(target, pattern, params));
                break;
            case "Integer":
                checkersBuilder.add(new IntegerChecker(target, params));
                break;
            case "Float":
                checkersBuilder.add(new FloatChecker(target, params));
                break;
            case "RegularExpression":
                pattern = params.get(RegularExpressionChecker.PARAM_PATTERN);
                checkersBuilder.add(new RegularExpressionChecker(target, pattern, params));
                break;
            case "Reference":
                String refType = checkerDescription.getParams().get(ReferenceLineChecker.PARAM_REFTYPE);
                ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
                ImmutableMap<String, UUID> referenceValues = referenceValueRepository.getReferenceIdPerKeys(refType);
                checkersBuilder.add(new ReferenceLineChecker(target, refType, referenceValues, null, params));
                break;
            default:
                throw new IllegalArgumentException("checker inconnu " + checkerDescription.getName());
        }
    }

    private List<CheckerTarget> buildCheckerTarget(Map<String, String> params) {
        String columnsString = params.getOrDefault(COLUMNS, null);
        String variableComponentKeyParam = params.getOrDefault(VARIABLE_COMPONENT_KEY, null);
        if (!Strings.isNullOrEmpty(columnsString)) {
            return Stream.of(columnsString.split(","))
                    .map(column -> CheckerTarget.getInstance(column))
                    .collect(Collectors.toList());
        } else if (!Strings.isNullOrEmpty(variableComponentKeyParam) || !variableComponentKeyParam.matches("_")) {
            String[] split = variableComponentKeyParam.split("_");
            Stream.of(new VariableComponentKey(split[0], split[1]))
                    .map(variableComponentKey -> CheckerTarget.getInstance(variableComponentKey))
                    .collect(Collectors.toList());

        }
        return null;
    }

    enum Type {
        REFERENCE(COLUMNS), DATATYPE(VARIABLE_COMPONENT_KEY);

        private final String param;

        Type(String requiredAttributeForCheckerOnOneVariableComponentLineChecker) {
            this.param = requiredAttributeForCheckerOnOneVariableComponentLineChecker;
        }

        public String getParam() {
            return param;
        }
    }
}