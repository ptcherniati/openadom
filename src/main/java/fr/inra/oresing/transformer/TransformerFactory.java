package fr.inra.oresing.transformer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.checker.GroovyConfiguration;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.groovy.StringGroovyExpression;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
@Slf4j
public class TransformerFactory {

    /**
     * Transformeur qui ne fait aucune transformation
     */
    private static final LineTransformer NULL_LINE_TRANSFORMER = new LineTransformer() {
        @Override
        public Datum transform(Datum values) {
            return values;
        }

        @Override
        public ReferenceDatum transform(ReferenceDatum referenceDatum) {
            return referenceDatum;
        }
    };

    @Autowired
    private OreSiRepository repository;

    @Autowired
    private GroovyContextHelper groovyContextHelper;

    public LineTransformer newTransformer(TransformationConfiguration configuration, Application app, CheckerTarget target) {
        ReferenceValueRepository referenceValueRepository = repository.getRepository(app).referenceValue();
        ImmutableList.Builder<LineTransformer> transformersBuilder = ImmutableList.builder();
        if (configuration.isCodify()) {
            transformersBuilder.add(new CodifyOneLineElementTransformer(target));
        }
        if (configuration.getGroovy() != null) {
            GroovyConfiguration groovyConfiguration = configuration.getGroovy();
            String expression = groovyConfiguration.getExpression();
            StringGroovyExpression groovyExpression = StringGroovyExpression.forExpression(expression);
            Set<String> references = groovyConfiguration.getReferences();
            ImmutableMap<String, Object> groovyContext = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, references);
            Preconditions.checkState(groovyConfiguration.getDatatypes().isEmpty(), "à ce stade, on ne gère pas la chargement de données");
            TransformOneLineElementTransformer transformer = new GroovyExpressionOnOneLineElementTransformer(groovyExpression, groovyContext, target);
            transformersBuilder.add(transformer);
        }
        ImmutableList<LineTransformer> transformers = transformersBuilder.build();
        return new ChainTransformersLineTransformer(transformers);
    }

    public LineTransformer getNullTransformer() {
        return NULL_LINE_TRANSFORMER;
    }
}
