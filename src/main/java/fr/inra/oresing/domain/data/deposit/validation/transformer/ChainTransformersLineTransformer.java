package fr.inra.oresing.domain.data.deposit.validation.transformer;

import com.google.common.collect.ImmutableList;
import fr.inra.oresing.domain.data.Datum;
import fr.inra.oresing.domain.data.DataDatum;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Map;

/**
 * Un transformeur qui est juste le chaînage de plusieurs transformeurs.
 */
public class ChainTransformersLineTransformer implements LineTransformer {

    private final ImmutableList<LineTransformer> transformers;

    public ChainTransformersLineTransformer(final ImmutableList<LineTransformer> transformers) {
        super();
        this.transformers = transformers;
    }

    @Override
    public DataDatum transform(final DataDatum referenceDatumBeforeTransformation, Map<String, Object> context) {
        final Deque<DataDatum> transformations = new LinkedList<>();
        transformations.add(referenceDatumBeforeTransformation);
        transformers.forEach(lineTransformer -> {
            final DataDatum datumAfterLastTransformation = transformations.getLast();
            final DataDatum datumAfterOneMoreTransformation = lineTransformer.transform(datumAfterLastTransformation, context);
            transformations.add(datumAfterOneMoreTransformation);
        });
        return transformations.getLast();
    }

    @Override
    public Datum transform(final Datum DatumBeforeTransformation) {
        final Deque<Datum> transformations = new LinkedList<>();
        transformations.add(DatumBeforeTransformation);
        transformers.forEach(lineTransformer -> {
            final Datum datumAfterLastTransformation = transformations.getLast();
            final Datum datumAfterOneMoreTransformation = lineTransformer.transform(datumAfterLastTransformation);
            transformations.add(datumAfterOneMoreTransformation);
        });
        return transformations.getLast();
    }
}
