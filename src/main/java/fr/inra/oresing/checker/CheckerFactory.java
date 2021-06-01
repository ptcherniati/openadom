package fr.inra.oresing.checker;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Component
@Slf4j
public class CheckerFactory {

    @Autowired
    private OreSiRepository repository;

    public ImmutableMap<VariableComponentKey, ReferenceLineChecker> getReferenceLineCheckers(Application app, String dataType) {
        return getLineCheckers(app, dataType).stream()
                .filter(lineChecker -> lineChecker instanceof ReferenceLineChecker)
                .map(lineChecker -> (ReferenceLineChecker) lineChecker)
                .collect(ImmutableMap.toImmutableMap(ReferenceLineChecker::getVariableComponentKey, Function.identity()));
    }

    public ImmutableSet<LineChecker> getLineCheckers(Application app, String dataType) {
        Preconditions.checkArgument(app.getConfiguration().getDataTypes().containsKey(dataType), "Pas de type de données " + dataType + " dans " + app);
        Configuration.DataTypeDescription dataTypeDescription = app.getConfiguration().getDataTypes().get(dataType);
        ImmutableSet.Builder<LineChecker> checkersBuilder = ImmutableSet.builder();
        for (Map.Entry<String, Configuration.ColumnDescription> variableEntry : dataTypeDescription.getData().entrySet()) {
            String variable = variableEntry.getKey();
            Configuration.ColumnDescription variableDescription = variableEntry.getValue();
            for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : variableDescription.getComponents().entrySet()) {
                String component = componentEntry.getKey();
                VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
                if (variableDescription.getComponents().get(component) == null) {
                    if (log.isDebugEnabled()) {
                       log.debug("pas de règle de validation pour " + variableComponentKey);
                    }
                } else {
                    Configuration.CheckerDescription checkerDescription = variableDescription.getComponents().get(component).getChecker();
                    CheckerOnOneVariableComponentLineChecker variableComponentChecker;
                    if ("Reference".equals(checkerDescription.getName())) {
                        String refType = checkerDescription.getParams().get(ReferenceLineChecker.PARAM_REFTYPE);
                        ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
                        String keysColumnsSearch = checkerDescription.getParams().get(ReferenceLineChecker.PARAM_KEYSCOLUMNSSEARCH);
                        String patternKey = checkerDescription.getParams().get(ReferenceLineChecker.PARAM_PATTERNKEY);
                        ImmutableMap<String, UUID> referenceValues;
                        if(Strings.isNullOrEmpty(keysColumnsSearch) || Strings.isNullOrEmpty(patternKey)){
                            referenceValues = referenceValueRepository.getReferenceIdPerKeys(refType);
                            variableComponentChecker = new ReferenceLineChecker(variableComponentKey, refType, referenceValues);
                        }else {
                            referenceValues = referenceValueRepository.getReferenceIdPerColumnKeys(refType, keysColumnsSearch, keysColumnsSearch);
                            variableComponentChecker = new ReferenceLineChecker(variableComponentKey, refType, referenceValues, patternKey);
                        }
                    } else if ("Date".equals(checkerDescription.getName())) {
                        String pattern = checkerDescription.getParams().get(DateLineChecker.PARAM_PATTERN);
                        variableComponentChecker = new DateLineChecker(variableComponentKey, pattern);
                    } else if ("Integer".equals(checkerDescription.getName())) {
                        variableComponentChecker = new IntegerChecker(variableComponentKey);
                    } else if ("Float".equals(checkerDescription.getName())) {
                        variableComponentChecker = new FloatChecker(variableComponentKey);
                    } else if ("RegularExpression".equals(checkerDescription.getName())) {
                        String pattern = checkerDescription.getParams().get(RegularExpressionChecker.PARAM_PATTERN);
                        variableComponentChecker = new RegularExpressionChecker(variableComponentKey, pattern);
                    } else {
                        throw new IllegalArgumentException("checker inconnu " + checkerDescription.getName());
                    }
                    Preconditions.checkState(variableComponentChecker.getVariableComponentKey().equals(variableComponentKey));
                    checkersBuilder.add(variableComponentChecker);
                }
            }
        }
        for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationEntry : dataTypeDescription.getValidations().entrySet()) {
            Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationEntry.getValue();
            Configuration.CheckerDescription checkerDescription = lineValidationRuleDescription.getChecker();
            LineChecker lineChecker;
            if (GroovyLineChecker.NAME.equals(checkerDescription.getName())) {
                String expression = checkerDescription.getParams().get(GroovyLineChecker.PARAM_EXPRESSION);
                lineChecker = GroovyLineChecker.forExpression(expression);
            } else {
                throw new IllegalArgumentException("checker inconnu " + checkerDescription.getName());
            }
            checkersBuilder.add(lineChecker);
        }
        ImmutableSet<LineChecker> lineCheckers = checkersBuilder.build();
        if (log.isTraceEnabled()) {
            log.trace("pour " + app.getName() + ", " + dataType + ", on validera avec " + lineCheckers);
        }
        return lineCheckers;
    }
}
