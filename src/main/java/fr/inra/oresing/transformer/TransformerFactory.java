package fr.inra.oresing.transformer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.groovy.StringGroovyExpression;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

@Component
@Slf4j
public class TransformerFactory {

    @Autowired
    private OreSiRepository repository;

    @Autowired
    private GroovyContextHelper groovyContextHelper;

    public ImmutableSet<LineTransformer> getDataTypeLineTransformers(Application app, String dataType) {
        Preconditions.checkArgument(app.getConfiguration().getDataTypes().containsKey(dataType), "Pas de type de données " + dataType + " dans " + app);
        Configuration.DataTypeDescription dataTypeDescription = app.getConfiguration().getDataTypes().get(dataType);
        ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
        ImmutableSet.Builder<LineTransformer> transformersBuilder = ImmutableSet.builder();
        for (Map.Entry<String, Configuration.ColumnDescription> variableEntry : dataTypeDescription.getData().entrySet()) {
            String variable = variableEntry.getKey();
            Configuration.ColumnDescription variableDescription = variableEntry.getValue();
            for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : variableDescription.getComponents().entrySet()) {
                String component = componentEntry.getKey();
                VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
                if (variableDescription.getComponents().get(component) == null) {
                    if (log.isDebugEnabled()) {
                        //log.debug("pas de règle de validation pour " + variableComponentKey);
                    }
                } else {
                    Configuration.CheckerDescription checkerDescription = variableDescription.getComponents().get(component).getChecker();
                    CheckerTarget target = CheckerTarget.getInstance(variableComponentKey, app, repository.getRepository(app));
                    TransformationConfiguration configuration = checkerDescription.getParams();
                    if (configuration == null) {
                        // possible car `params` peut être vide dans le cas d'un checker de type Integer
                    } else {
                        if (configuration.isCodify()) {
                            transformersBuilder.add(new CodifyOneLineElementTransformer(target));
                        }
                        if (configuration.getGroovy() != null) {
                            String groovy = configuration.getGroovy();
                            StringGroovyExpression groovyExpression = StringGroovyExpression.forExpression(groovy);
                            Set<String> references = configuration.doGetReferencesAsCollection();
                            ImmutableMap<String, Object> groovyContext = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, references);
                            TransformOneLineElementTransformer transformer = new GroovyExpressionOnOneLineElementTransformer(groovyExpression, groovyContext, target);
                            transformersBuilder.add(transformer);
                        }
                    }
                }
            }
        }
        ImmutableSet<LineTransformer> lineTransformers = transformersBuilder.build();
        if (log.isTraceEnabled()) {
            log.trace("pour " + app.getName() + ", " + dataType + ", on transformera avec " + lineTransformers);
        }
        return lineTransformers;
    }

    public ImmutableSet<LineTransformer> getReferenceLineTransformers(Application app, String reference) {
        Preconditions.checkArgument(app.getConfiguration().getReferences().containsKey(reference), "Pas de référence " + reference + " dans " + app);
        ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
        Configuration.ReferenceDescription referenceDescription = app.getConfiguration().getReferences().get(reference);
        ImmutableSet.Builder<LineTransformer> transformersBuilder = ImmutableSet.builder();
        for (Map.Entry<String, Configuration.LineValidationRuleDescription> validationEntry : referenceDescription.getValidations().entrySet()) {
            String validation = validationEntry.getKey();
            Configuration.LineValidationRuleDescription lineValidationRuleDescription = validationEntry.getValue();
            Configuration.CheckerConfigurationDescription params = lineValidationRuleDescription.getChecker().getParams();
            TransformationConfiguration configuration = params;
            params.doGetColumnsAsCollection().stream()
                    .map(ReferenceColumn::new)
                    .map(referenceColumn -> CheckerTarget.getInstance(referenceColumn, app, null))
                    .forEach(target -> {
                        if (configuration.isCodify()) {
                            transformersBuilder.add(new CodifyOneLineElementTransformer(target));
                        }
                        if (configuration.getGroovy() != null) {
                            String groovy = configuration.getGroovy();
                            StringGroovyExpression groovyExpression = StringGroovyExpression.forExpression(groovy);
                            Set<String> references = configuration.doGetReferencesAsCollection();
                            ImmutableMap<String, Object> groovyContext = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, references);
                            TransformOneLineElementTransformer transformer = new GroovyExpressionOnOneLineElementTransformer(groovyExpression, groovyContext, target);
                            transformersBuilder.add(transformer);
                        }
                    });
        }
        ImmutableSet<LineTransformer> lineTransformers = transformersBuilder.build();
        if (log.isTraceEnabled()) {
            log.trace("pour " + app.getName() + ", " + reference + ", on transformera avec " + lineTransformers);
        }
        return lineTransformers;
    }
}
