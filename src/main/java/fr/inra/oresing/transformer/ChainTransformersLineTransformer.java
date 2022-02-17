package fr.inra.oresing.transformer;

import com.google.common.collect.ImmutableList;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.ReferenceDatum;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Un transformeur qui est juste le chaînage de plusieurs transformeurs.
 */
public class ChainTransformersLineTransformer implements LineTransformer {

    private final ImmutableList<LineTransformer> transformers;

    public ChainTransformersLineTransformer(ImmutableList<LineTransformer> transformers) {
        this.transformers = transformers;
    }

    @Override
    public ReferenceDatum transform(ReferenceDatum referenceDatumBeforeTransformation) {
        Deque<ReferenceDatum> transformations = new LinkedList<>();
        transformations.add(referenceDatumBeforeTransformation);
        transformers.forEach(lineTransformer -> {
            ReferenceDatum datumAfterLastTransformation = transformations.getLast();
            ReferenceDatum datumAfterOneMoreTransformation = lineTransformer.transform(datumAfterLastTransformation);
            transformations.add(datumAfterOneMoreTransformation);
        });
        ReferenceDatum datumAfterFullTransformation = transformations.getLast();
        return datumAfterFullTransformation;
    }

    @Override
    public Datum transform(Datum DatumBeforeTransformation) {
        Deque<Datum> transformations = new LinkedList<>();
        transformations.add(DatumBeforeTransformation);
        transformers.forEach(lineTransformer -> {
            Datum datumAfterLastTransformation = transformations.getLast();
            Datum datumAfterOneMoreTransformation = lineTransformer.transform(datumAfterLastTransformation);
            transformations.add(datumAfterOneMoreTransformation);
        });
        Datum datumAfterFullTransformation = transformations.getLast();
        return datumAfterFullTransformation;
    }
}
