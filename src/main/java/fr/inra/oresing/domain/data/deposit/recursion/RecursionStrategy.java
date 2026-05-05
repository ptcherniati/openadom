package fr.inra.oresing.domain.data.deposit.recursion;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Représente les variations de l'algorithme d'import selon que le référentiel soit récursif ou non.
 */
public interface RecursionStrategy {

    Ltree getHierarchicalKey(Ltree naturalKey, DataDatum referenceDatum, ReferenceDatumAfterChecking referenceDatumAfterChecking);

    AsynchroneFileImporterContext dataImporterContext();

    static Function<Ltree, Ltree> toNaturalKey(String dataname) {
        return nk -> Ltree.fromSql("%sK%s".formatted(dataname, nk.getSql()));
    }

    Ltree computeNaturalKey(ReferenceDatumAfterChecking referenceDatumAfterChecking);

    List<ReferenceDatumAfterChecking> testHasParent(Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey, RecursionStrategy recursionStrategy, ReferenceDatumAfterChecking referenceDatumAfterChecking);

    void addKnownIdToReferenceValues(DataValue.LineIdentityColumnName key, UUID newUuid);

    Map<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> getReferenceValuesForSelfType();

    void addReferenceValuesForSelfType(DataValue.LineIdentityColumnName key, ImmutableSet<UUID> uuids);

    /**
     * Retourne {@code true} si cette stratégie opère en mode « récursion ordonnée »
     * ({@code __ORDER_STRICT__} ou {@code orderedRecursionMode=true}).
     * Par défaut {@code false} (mode standard, lazy-loading des parents).
     */
    default boolean isOrderedMode() {
        return false;
    }
}