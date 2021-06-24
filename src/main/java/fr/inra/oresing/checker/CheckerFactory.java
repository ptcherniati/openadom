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
import org.springframework.util.CollectionUtils;

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
                .map(lineChecker -> lineChecker instanceof ILineCheckerDecorator?((ILineCheckerDecorator) lineChecker).getChecker():lineChecker)
                .filter(lineChecker -> lineChecker instanceof ReferenceLineChecker)
                .map(lineChecker -> (ReferenceLineChecker) lineChecker)
                .collect(ImmutableMap.toImmutableMap(ReferenceLineChecker::getVariableComponentKey, Function.identity()));
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
                        ImmutableMap<String, UUID> referenceValues = referenceValueRepository.getReferenceIdPerKeys(refType);
                        variableComponentChecker = new ReferenceLineChecker(variableComponentKey, refType, referenceValues);
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
                    boolean hasRequiredParam = Optional.ofNullable(checkerDescription)
                            .map(cd->cd.getParams())
                            .filter(p->p.containsKey(RequiredChecker.PARAMS_REQUIRED))
                            .isPresent();
                    if(hasRequiredParam){
                        String requiredString = checkerDescription.getParams().get(RequiredChecker.PARAMS_REQUIRED);
                        if(requiredString==null || "true".equalsIgnoreCase(requiredString)){
                            variableComponentChecker = RequiredChecker.requiredChecker(variableComponentChecker);
                        }else{
                            variableComponentChecker = RequiredChecker.notRequiredChecker(variableComponentChecker);
                        }
                    }
                    checkersBuilder.add(variableComponentChecker);
                }
            }
        }
        addCheckersFromLineValidationDescriptions(app, dataTypeDescription.getValidations(), checkersBuilder, Type.DATATYPE.getParam()); //Configuration.DataTypeDescription dataTypeDescription,
        ImmutableSet<LineChecker> lineCheckers = checkersBuilder.build();
        if (log.isTraceEnabled()) {
            log.trace("pour " + app.getName() + ", " + dataType + ", on validera avec " + lineCheckers);
        }
        return lineCheckers;
    }

    private void addCheckersFromLineValidationDescriptions(Application app, LinkedHashMap<String, Configuration.LineValidationRuleDescription> lineValidationDescriptions, ImmutableSet.Builder<LineChecker> checkersBuilder, String param) {
        for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationEntry : lineValidationDescriptions.entrySet()) {
            Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationEntry.getValue();
            Configuration.CheckerDescription checkerDescription = lineValidationRuleDescription.getChecker();
            LineChecker lineChecker;
            Map<String, String> params = checkerDescription.getParams();
            VariableComponentKey variableComponentKey = buildVariableComponentKey(params);
            String pattern;
            if (GroovyLineChecker.NAME.equals(checkerDescription.getName())) {
                String expression = params.get(GroovyLineChecker.PARAM_EXPRESSION);
                lineChecker = GroovyLineChecker.forExpression(expression);
                checkersBuilder.add(lineChecker);
            } else {
                List<String> columns = buildColumns(params);
                if (variableComponentKey != null) {
                    buildCheckers(app, checkerDescription, params, null, variableComponentKey, checkersBuilder);
                } else if (!CollectionUtils.isEmpty(columns)) {
                    columns.forEach(column -> buildCheckers(app, checkerDescription, params, column, null, checkersBuilder));
                } else {
                    throw new IllegalArgumentException(String.format("Pour le checker de ligne %s, le paramètre %s doit être fourni.", checkerDescription.getName(), param));
                }
            }
            checkersBuilder.build();
        }
    }

    private void buildCheckers(Application app, Configuration.CheckerDescription checkerDescription, Map<String, String> params, String column, VariableComponentKey variableComponentKey, ImmutableSet.Builder<LineChecker> checkersBuilder) {
        String pattern;
        switch (checkerDescription.getName()) {
            case "Date":
                pattern = params.get(DateLineChecker.PARAM_PATTERN);
                checkersBuilder.add(variableComponentKey == null ? new DateLineChecker(column, pattern) : new DateLineChecker(variableComponentKey, pattern));
                break;
            case "Integer":
                checkersBuilder.add(variableComponentKey == null ? new IntegerChecker(column) : new IntegerChecker(variableComponentKey));
                break;
            case "Float":
                checkersBuilder.add(variableComponentKey == null ? new FloatChecker(column) : new FloatChecker(variableComponentKey));
                break;
            case "RegularExpression":
                pattern = params.get(RegularExpressionChecker.PARAM_PATTERN);
                checkersBuilder.add(variableComponentKey == null ? new RegularExpressionChecker(column, pattern) : new RegularExpressionChecker(variableComponentKey, pattern));
                break;
            case "Reference":
                String refType = checkerDescription.getParams().get(ReferenceLineChecker.PARAM_REFTYPE);
                ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
                ImmutableMap<String, UUID> referenceValues = referenceValueRepository.getReferenceIdPerKeys(refType);
                checkersBuilder.add(variableComponentKey == null ? new ReferenceLineChecker(column, refType, referenceValues) : new ReferenceLineChecker(variableComponentKey, refType, referenceValues));
                break;
            default:
                throw new IllegalArgumentException("checker inconnu " + checkerDescription.getName());
        }
    }

    private List<String> buildColumns(Map<String, String> params) {
        String columnsString = params.getOrDefault(COLUMNS, null);
        if (Strings.isNullOrEmpty(columnsString)) {
            return null;
        }
        return Stream.of(columnsString.split(",")).collect(Collectors.toList());
    }

    private VariableComponentKey buildVariableComponentKey(Map<String, String> params) {
        String variableComponentKey = params.getOrDefault(VARIABLE_COMPONENT_KEY, null);
        if (Strings.isNullOrEmpty(variableComponentKey) || !variableComponentKey.matches("_")) {
            return null;
        }
        String[] split = variableComponentKey.split("_");
        return new VariableComponentKey(split[0], split[1]);
    }

    enum Type {
        REFERENCE("columns"), DATATYPE("variableComponentKey");

        public String getParam() {
            return param;
        }

        private final String param;

        private Type(String requiredAttributeForCheckerOnOneVariableComponentLineChecker) {
            this.param = requiredAttributeForCheckerOnOneVariableComponentLineChecker;
        }
    }
}
