package fr.inra.oresing.checker;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.VariableComponentKey;
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
                .collect(ImmutableMap.toImmutableMap(rlc -> (VariableComponentKey) rlc.getTarget(), Function.identity()));
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
            LineTransformer transformer = Optional.ofNullable(checkerDescription.getParams())
                    .map(transformationConfiguration -> transformerFactory.newTransformer(transformationConfiguration, app, variableComponentKey))
                    .orElseGet(transformerFactory::getNullTransformer);
            if ("Reference".equals(checkerDescription.getName())) {
                variableComponentChecker = getCheckerOnReferenceChecker(app, dataType, locale, checkerDescription, variableComponentKey, transformer);
            } else {
                final Configuration.CheckerConfigurationDescription configuration = checkerDescription.getParams();
                if ("Date".equals(checkerDescription.getName())) {
                    String pattern = configuration.getPattern();
                    variableComponentChecker = new DateLineChecker(variableComponentKey, pattern, configuration, transformer);
                } else if ("Integer".equals(checkerDescription.getName())) {
                    Preconditions.checkState(configuration == null || !configuration.isCodify(), "codify avec checker " + checkerDescription.getName() + " sur le composant " + component + " de la variable " + variable + " du type de données " + dataType + " de l'application " + app.getName());
                    variableComponentChecker = new IntegerChecker(variableComponentKey, configuration, transformer);
                } else if ("Float".equals(checkerDescription.getName())) {
                    Preconditions.checkState(configuration == null || !configuration.isCodify(), "codify avec checker " + checkerDescription.getName() + " sur le composant " + component + " de la variable " + variable + " du type de données " + dataType + " de l'application " + app.getName());
                    variableComponentChecker = new FloatChecker(variableComponentKey, configuration, transformer);
                } else if ("RegularExpression".equals(checkerDescription.getName())) {
                    String pattern = configuration.getPattern();
                    variableComponentChecker = new RegularExpressionChecker(variableComponentKey, pattern, configuration, transformer);
                } else {
                    throw new IllegalArgumentException("checker inconnu " + checkerDescription.getName());
                }
            }
            Preconditions.checkState(variableComponentChecker.getTarget().equals(variableComponentKey));
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

    private void addCheckersFromLineValidationDescriptions(Application app, LinkedHashMap<String, Configuration.LineValidationRuleDescription> lineValidationDescriptions, ImmutableSet.Builder<LineChecker> checkersBuilder, String param) {
        ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
        DataRepository dataRepository = repository.getRepository(app).data();
        for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationEntry : lineValidationDescriptions.entrySet()) {
            Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationEntry.getValue();
            Configuration.CheckerDescription checkerDescription = lineValidationRuleDescription.getChecker();
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
                LineChecker lineChecker = GroovyLineChecker.forExpression(expression, context, configurationDescription);
                checkersBuilder.add(lineChecker);
            } else {
                List<CheckerOnOneVariableComponentLineChecker> lineCheckers = configurationDescription.doGetColumnsAsCollection().stream()
                        .map(ReferenceColumn::new)
                        .map(checkerTarget -> newChecker(app, checkerDescription, checkerTarget))
                        .collect(Collectors.toList());
                checkersBuilder.addAll(lineCheckers);
            }
            checkersBuilder.build();
        }
    }

    private CheckerOnOneVariableComponentLineChecker newChecker(Application app, Configuration.CheckerDescription checkerDescription, CheckerTarget target) {
        LineTransformer transformer = transformerFactory.newTransformer(checkerDescription.getParams(), app, target);
        Configuration.CheckerConfigurationDescription checkerConfigurationDescription = checkerDescription.getParams();
        CheckerOnOneVariableComponentLineChecker lineChecker;
        switch (checkerDescription.getName()) {
            case "Date":
                lineChecker = new DateLineChecker(target, checkerConfigurationDescription.getPattern(), checkerConfigurationDescription, transformer);
                break;
            case "Integer":
                lineChecker = new IntegerChecker(target, checkerConfigurationDescription, transformer);
                break;
            case "Float":
                lineChecker = new FloatChecker(target, checkerConfigurationDescription, transformer);
                break;
            case "RegularExpression":
                lineChecker = new RegularExpressionChecker(target, checkerConfigurationDescription.getPattern(), checkerConfigurationDescription, transformer);
                break;
            case "Reference":
                String refType = checkerConfigurationDescription.getRefType();
                ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
                ImmutableMap<Ltree, UUID> referenceValues = referenceValueRepository.getReferenceIdPerKeys(refType);
                lineChecker = new ReferenceLineChecker(target, refType, referenceValues, checkerConfigurationDescription, transformer);
                break;
            default:
                throw new IllegalArgumentException("checker inconnu " + checkerDescription.getName());
        }
        return lineChecker;
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