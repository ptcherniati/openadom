package fr.inra.oresing.checker;

import com.google.common.base.MoreObjects;
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
import fr.inra.oresing.transformer.LineTransformer;
import fr.inra.oresing.transformer.TransformerFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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
        for (Map.Entry<String, Configuration.ReferenceStaticColumnDescription> variableEntry : referenceDescription.doGetStaticColumnDescriptions().entrySet()) {
            String column = variableEntry.getKey();
            ReferenceColumn referenceColumn = new ReferenceColumn(column);
            Configuration.ReferenceStaticColumnDescription variableDescription = variableEntry.getValue();
            Optional.ofNullable(variableEntry.getValue())
                    .map(Configuration.ReferenceStaticColumnDescription::getChecker)
                    .map(checkerDescription -> newChecker(app, checkerDescription, referenceColumn))
                    .ifPresent(checkersBuilder::add);
        }
        ImmutableMap<String, Configuration.LineValidationRuleDescription> validations = ImmutableMap.copyOf(referenceDescription.getValidations());
        checkersBuilder.addAll(validationRulesToLineCheckers(app, validations));
        ImmutableSet<LineChecker> lineCheckers = checkersBuilder.build();
        if (log.isTraceEnabled()) {
            log.trace("pour " + app.getName() + ", " + reference + ", on validera avec " + lineCheckers);
        }
        return lineCheckers;
    }

    public ImmutableSet<LineChecker> getLineCheckers(Application app, String dataType) {
        Preconditions.checkArgument(app.getConfiguration().getDataTypes().containsKey(dataType), "Pas de type de données " + dataType + " dans " + app);
        Configuration.DataTypeDescription dataTypeDescription = app.getConfiguration().getDataTypes().get(dataType);
        ImmutableSet.Builder<LineChecker> checkersBuilder = ImmutableSet.builder();
        for (Map.Entry<String, Configuration.VariableDescription> variableEntry : dataTypeDescription.getData().entrySet()) {
            String variable = variableEntry.getKey();
            Configuration.VariableDescription variableDescription = variableEntry.getValue();
            for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : variableDescription.doGetAllComponentDescriptions().entrySet()) {
                String component = componentEntry.getKey();
                VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
                Optional.ofNullable(componentEntry.getValue())
                        .map(Configuration.VariableComponentDescription::getChecker)
                        .map(checkerDescription -> newChecker(app, checkerDescription, variableComponentKey))
                        .ifPresent(checkersBuilder::add);
            }
        }
        ImmutableMap<String, Configuration.LineValidationRuleDescription> validations = ImmutableMap.copyOf(dataTypeDescription.getValidations());
        checkersBuilder.addAll(validationRulesToLineCheckers(app, validations));
        ImmutableSet<LineChecker> lineCheckers = checkersBuilder.build();
        if (log.isTraceEnabled()) {
            log.trace("pour " + app.getName() + ", " + dataType + ", on validera avec " + lineCheckers);
        }
        return lineCheckers;
    }

    private CheckerOnOneVariableComponentLineChecker newChecker(Application app, Configuration.CheckerDescription checkerDescription, CheckerTarget target) {
        Configuration.CheckerConfigurationDescription configuration =
                MoreObjects.firstNonNull(
                        checkerDescription.getParams(),
                        new Configuration.CheckerConfigurationDescription()
                );
        LineTransformer transformer = transformerFactory.newTransformer(configuration.getTransformation(), app, target);
        CheckerOnOneVariableComponentLineChecker lineChecker = null;
        switch (checkerDescription.getName()) {
            case Date:
                lineChecker = new DateLineChecker(target, configuration.getPattern(), configuration, transformer);
                break;
            case Integer:
                lineChecker = new IntegerChecker(target, configuration, transformer);
                break;
            case Float:
                lineChecker = new FloatChecker(target, configuration, transformer);
                break;
            case RegularExpression:
                lineChecker = new RegularExpressionChecker(target, configuration.getPattern(), configuration, transformer);
                break;
            case Reference:
                String refType = configuration.getRefType();
                ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
                ImmutableMap<Ltree, UUID> referenceValues = referenceValueRepository.getReferenceIdPerKeys(refType);
                lineChecker = new ReferenceLineChecker(target, refType, referenceValues, configuration, transformer);
                break;
            default:
                CheckerType.getError(checkerDescription.getName(), Set.of( CheckerType.Date,CheckerType.Integer,CheckerType.Float, CheckerType.RegularExpression,CheckerType.Reference));
        }
        Preconditions.checkState(lineChecker.getTarget().equals(target));
        return lineChecker;
    }

    private LineChecker newLineChecker(Application app, Configuration.LineValidationRuleDescription lineValidationRuleDescription) {
        Configuration.CheckerDescription checkerDescription = lineValidationRuleDescription.getChecker();
        Configuration.CheckerConfigurationDescription configurationDescription = checkerDescription.getParams();
        LineChecker lineChecker=null;
        if (CheckerType.GroovyExpression.equals(checkerDescription.getName())) {
            String expression = configurationDescription.getGroovy().getExpression();
            Set<String> references = configurationDescription.getGroovy().getReferences();
            Set<String> dataTypes = configurationDescription.getGroovy().getDatatypes();
            ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
            ImmutableMap<String, Object> groovyContextForReferences = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, references,null);
            DataRepository dataRepository = repository.getRepository(app).data();
            ImmutableMap<String, Object> groovyContextForDataTypes = groovyContextHelper.getGroovyContextForDataTypes(dataRepository, dataTypes, app);
            ImmutableMap<String, Object> context = ImmutableMap.<String, Object>builder()
                    .putAll(groovyContextForReferences)
                    .putAll(groovyContextForDataTypes)
                    .put("application", app)
                    .build();
            lineChecker = GroovyLineChecker.forExpression(expression, context, configurationDescription);
        } else {
            throw CheckerType.getError(checkerDescription.getName(), Set.of(CheckerType.GroovyExpression));
        }
        return lineChecker;
    }

    private ImmutableSet<LineChecker> validationRulesToLineCheckers(Application app, ImmutableMap<String, Configuration.LineValidationRuleDescription> validations) {
        ImmutableSet.Builder<LineChecker> checkersBuilder = ImmutableSet.builder();
        for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationEntry : validations.entrySet()) {
            Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationEntry.getValue();
            Configuration.CheckerDescription checkerDescription = lineValidationRuleDescription.getChecker();
            if (CheckerType.GroovyExpression.equals(checkerDescription.getName())) {
                LineChecker lineChecker = newLineChecker(app, lineValidationRuleDescription);
                checkersBuilder.add(lineChecker);
            } else {
                List<CheckerOnOneVariableComponentLineChecker> lineCheckers = lineValidationRuleDescription.doGetCheckerTargets().stream()
                        .map(checkerTarget -> newChecker(app, checkerDescription, checkerTarget))
                        .collect(Collectors.toList());
                checkersBuilder.addAll(lineCheckers);
            }
        }
        return checkersBuilder.build();
    }
}