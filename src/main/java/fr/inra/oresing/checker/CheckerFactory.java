package fr.inra.oresing.checker;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.model.internationalization.InternationalizationDisplay;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import fr.inra.oresing.rest.ApplicationResult;
import fr.inra.oresing.transformer.LineTransformer;
import fr.inra.oresing.transformer.TransformerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@Slf4j
public class CheckerFactory {

    @Deprecated
    public static final String COLUMNS = "columns";

    @Deprecated
    public static final String VARIABLE_COMPONENT_KEY = "variableComponentKey";

    @Autowired
    private OreSiRepository repository;

    @Autowired
    private GroovyContextHelper groovyContextHelper;

    @Autowired
    private TransformerFactory transformerFactory;

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
                //log.debug("pas de règle de validation pour " + variableComponentKey);
            }
        } else {
            Configuration.CheckerDescription checkerDescription = variableDescription.getComponents().get(component).getChecker();
            CheckerOnOneVariableComponentLineChecker variableComponentChecker;
            CheckerTarget checkerTarget = CheckerTarget.getInstance(variableComponentKey, app, repository.getRepository(app));
            LineTransformer transformer = Optional.ofNullable(checkerDescription.getParams())
                    .map(transformationConfiguration -> transformerFactory.newTransformer(transformationConfiguration, app, checkerTarget))
                    .orElseGet(transformerFactory::getNullTransformer);
            if ("Reference".equals(checkerDescription.getName())) {
                variableComponentChecker = getCheckerOnReferenceChecker(app, dataType, locale, checkerDescription, checkerTarget, transformer);
            } else {
                final Configuration.CheckerConfigurationDescription configuration = checkerDescription.getParams();
                if ("Date".equals(checkerDescription.getName())) {
                    String pattern = configuration.getPattern();
                    variableComponentChecker = new DateLineChecker(checkerTarget, pattern, configuration, transformer);
                } else if ("Integer".equals(checkerDescription.getName())) {
                    Preconditions.checkState(configuration == null || !configuration.isCodify(), "codify avec checker " + checkerDescription.getName() + " sur le composant " + component + " de la variable " + variable + " du type de données " + dataType + " de l'application " + app.getName());
                    variableComponentChecker = new IntegerChecker(checkerTarget, configuration, transformer);
                } else if ("Float".equals(checkerDescription.getName())) {
                    Preconditions.checkState(configuration == null || !configuration.isCodify(), "codify avec checker " + checkerDescription.getName() + " sur le composant " + component + " de la variable " + variable + " du type de données " + dataType + " de l'application " + app.getName());
                    variableComponentChecker = new FloatChecker(checkerTarget, configuration, transformer);
                } else if ("RegularExpression".equals(checkerDescription.getName())) {
                    String pattern = configuration.getPattern();
                    variableComponentChecker = new RegularExpressionChecker(checkerTarget, pattern, configuration, transformer);
                } else {
                    throw new IllegalArgumentException("checker inconnu " + checkerDescription.getName());
                }
            }
            Preconditions.checkState(variableComponentChecker.getTarget().getTarget().equals(variableComponentKey));
            checkersBuilder.add(variableComponentChecker);
        }
    }

    private CheckerOnOneVariableComponentLineChecker getCheckerOnReferenceChecker(Application app, String dataType, String locale, Configuration.CheckerDescription checkerDescription, CheckerTarget checkerTarget, LineTransformer transformer) {
        CheckerOnOneVariableComponentLineChecker variableComponentChecker;
        String refType = checkerDescription.getParams().getRefType();
        ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
        ImmutableMap<Ltree, UUID> referenceValues;
        if (locale == null) {
            referenceValues = referenceValueRepository.getReferenceIdPerKeys(refType);
        } else {
            ImmutableMap<Ltree, ApplicationResult.Reference.ReferenceUUIDAndDisplay> referenceIdAndDisplayPerKeys = referenceValueRepository.getReferenceIdAndDisplayPerKeys(refType, locale);
            referenceValues = ImmutableMap.copyOf(
                    referenceIdAndDisplayPerKeys.entrySet().stream()
                            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue().getUuid()))
            );
        }
        variableComponentChecker = new ReferenceLineChecker(checkerTarget, refType, referenceValues, checkerDescription.getParams(), transformer);
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
        ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
        DataRepository dataRepository = repository.getRepository(app).data();
        for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationEntry : lineValidationDescriptions.entrySet()) {
            Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationEntry.getValue();
            Configuration.CheckerDescription checkerDescription = lineValidationRuleDescription.getChecker();
            LineChecker lineChecker;
            Configuration.CheckerConfigurationDescription configurationDescription = checkerDescription.getParams();
            if (GroovyLineChecker.NAME.equals(checkerDescription.getName())) {
                String expression = configurationDescription.getGroovy().getExpression();
                Set<String> references = configurationDescription.getGroovy().getReferences();
                Set<String> dataTypes = configurationDescription.getGroovy().getDatatypes();
                ImmutableMap<String, Object> groovyContextForReferences = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, references);
                ImmutableMap<String, Object> groovyContextForDataTypes = groovyContextHelper.getGroovyContextForDataTypes(dataRepository, dataTypes, app);
                ImmutableMap<String, Object> context = ImmutableMap.<String, Object>builder()
                        .putAll(groovyContextForReferences)
                        .putAll(groovyContextForDataTypes)
                        .put("application", app)
                        .build();
                lineChecker = GroovyLineChecker.forExpression(expression, context, configurationDescription);
                checkersBuilder.add(lineChecker);
            } else {
                List<CheckerTarget> checkerTargets = buildCheckerTarget(configurationDescription, app);
                if (checkerTargets != null) {
                    checkerTargets.forEach(checkerTarget -> buildCheckers(app, checkerDescription, checkerTarget, checkersBuilder));
                } else {
                    throw new IllegalArgumentException(String.format("Pour le checker de ligne %s, le paramètre %s doit être fourni.", checkerDescription.getName(), param));
                }
            }
            checkersBuilder.build();
        }
    }

    private void buildCheckers(Application app, Configuration.CheckerDescription checkerDescription, CheckerTarget target, ImmutableSet.Builder<LineChecker> checkersBuilder) {
        LineTransformer transformer = transformerFactory.newTransformer(checkerDescription.getParams(), app, target);
        Configuration.CheckerConfigurationDescription checkerConfigurationDescription = checkerDescription.getParams();
        switch (checkerDescription.getName()) {
            case "Date":
                checkersBuilder.add(new DateLineChecker(target, checkerConfigurationDescription.getPattern(), checkerConfigurationDescription, transformer));
                break;
            case "Integer":
                checkersBuilder.add(new IntegerChecker(target, checkerConfigurationDescription, transformer));
                break;
            case "Float":
                checkersBuilder.add(new FloatChecker(target, checkerConfigurationDescription, transformer));
                break;
            case "RegularExpression":
                checkersBuilder.add(new RegularExpressionChecker(target, checkerConfigurationDescription.getPattern(), checkerConfigurationDescription, transformer));
                break;
            case "Reference":
                String refType = checkerConfigurationDescription.getRefType();
                ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
                ImmutableMap<Ltree, UUID> referenceValues = referenceValueRepository.getReferenceIdPerKeys(refType);
                checkersBuilder.add(new ReferenceLineChecker(target, refType, referenceValues, checkerConfigurationDescription, transformer));
                break;
            default:
                throw new IllegalArgumentException("checker inconnu " + checkerDescription.getName());
        }
    }

    private List<CheckerTarget> buildCheckerTarget(Configuration.CheckerConfigurationDescription params, Application application) {
        String columnsString = params.getColumns();
        String variableComponentKeyParam = params.getVariableComponentKey();
        if (!Strings.isNullOrEmpty(columnsString)) {
            return Stream.of(columnsString.split(","))
                    .map(ReferenceColumn::new)
                    .map(referenceColumn -> CheckerTarget.getInstance(referenceColumn, application, repository.getRepository(application)))
                    .collect(Collectors.toList());
        } else if (!Strings.isNullOrEmpty(variableComponentKeyParam) || !variableComponentKeyParam.matches("_")) {
            String[] split = variableComponentKeyParam.split("_");
            Stream.of(new VariableComponentKey(split[0], split[1]))
                    .map(variableComponentKey -> CheckerTarget.getInstance(variableComponentKey, application, repository.getRepository(application)))
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