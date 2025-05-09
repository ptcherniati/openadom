package fr.inra.oresing.domain.data.deposit.recursion;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;

import java.util.List;
import java.util.function.Function;

/**
 * Représente les variations de l'algorithme d'import selon que le référentiel soit récursif ou non.
 */
public interface RecursionStrategy {

    Ltree getHierarchicalKey(Ltree naturalKey, DataDatum referenceDatum, ReferenceDatumAfterChecking referenceDatumAfterChecking);

    DataImporterContext dataImporterContext();

    Ltree computeNaturalKey(ReferenceDatumAfterChecking referenceDatumAfterChecking);

    List<ReferenceDatumAfterChecking> testHasParent(Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey, RecursionStrategy recursionStrategy, ReferenceDatumAfterChecking referenceDatumAfterChecking);
}