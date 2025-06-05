package fr.inra.oresing.domain.data.deposit.storage;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;

public record KeysAndReferenceDatumAfterChecking(ReferenceDatumAfterChecking referenceDatumAfterChecking,
                                                 Ltree naturalKey, Ltree hierarchicalKey) {
    public long getLineNumber() {
        return referenceDatumAfterChecking.lineNumber();
    }
}