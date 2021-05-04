package fr.inra.oresing.checker;

import com.google.common.base.Preconditions;
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
                    LineChecker lineChecker;
                    if ("Reference".equals(checkerDescription.getName())) {
                        String refType = checkerDescription.getParams().get(ReferenceLineChecker.PARAM_REFTYPE);
                        ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
                        ImmutableMap<String, UUID> referenceValues = referenceValueRepository.getReferenceIdPerKeys(refType);
                        lineChecker = new ReferenceLineChecker(variableComponentKey, refType, referenceValues);
                    } else if ("Date".equals(checkerDescription.getName())) {
                        String pattern = checkerDescription.getParams().get(DateLineChecker.PARAM_PATTERN);
                        lineChecker = new DateLineChecker(variableComponentKey, pattern);
                    } else if ("Integer".equals(checkerDescription.getName())) {
                        lineChecker = new IntegerChecker(variableComponentKey);
                    } else if ("Float".equals(checkerDescription.getName())) {
                        lineChecker = new FloatChecker(variableComponentKey);
                    } else if ("RegularExpression".equals(checkerDescription.getName())) {
                        String pattern = checkerDescription.getParams().get(RegularExpressionChecker.PARAM_PATTERN);
                        lineChecker = new RegularExpressionChecker(variableComponentKey, pattern);
                    } else {
                        throw new IllegalArgumentException("checker inconnu " + checkerDescription.getName());
                    }
                    checkersBuilder.add(lineChecker);
                }
            }
        }
        ImmutableSet<LineChecker> lineCheckers = checkersBuilder.build();
        if (log.isTraceEnabled()) {
            log.trace("pour " + app.getName() + ", " + dataType + ", on validera avec " + lineCheckers);
        }
        return lineCheckers;
    }
}
