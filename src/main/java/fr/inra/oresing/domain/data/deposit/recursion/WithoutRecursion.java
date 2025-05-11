package fr.inra.oresing.domain.data.deposit.recursion;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.data.DataColumnSingleValue;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.transformation.DataTransformer;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public record WithoutRecursion(DataImporterContext dataImporterContext) implements RecursionStrategy {

    @Override
    public Ltree computeNaturalKey(ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        UnaryOperator<String> nullOrEmptyToNull = partialKey -> Strings.isNullOrEmpty(partialKey) ? Ltree.NULL_KEY : partialKey;

        final String naturalKeyAsString = dataImporterContext.getKeyColumns().stream()
                .map(referenceColumn -> {
                    final DataColumnValue referenceColumnValue = referenceDatumAfterChecking.referenceDatumAfterChecking().get(referenceColumn);
                    Preconditions.checkState(referenceColumnValue instanceof DataColumnSingleValue, "dans le référentiel " + dataImporterContext.getRefType() + " la colonne " + referenceColumn + " est utilisée comme clé. Par conséquent, il ne peut pas y avoir une valeur multiple.");
                    return referenceColumnValue;
                })
                .map(DataColumnSingleValue.class::cast)
                .map(DataColumnSingleValue::getValue)
                .map(Object::toString)
                .map(nullOrEmptyToNull)
                .map(label -> label.matches(DateType.PATTERN_DATE_REGEXP_FIND_DATE) ? DateType.sorteableDateToFormattedDate(label).replace("/", "_") : label)
                .map(Ltree::escapeToLabel)
                .collect(Collectors.joining(DataImporterContext.getCompositeNaturalKeyComponentsSeparator()));
        Preconditions.checkState(!naturalKeyAsString.isEmpty(),
                ExceptionMessage.NULL_NATURAL_KEY.toMessage(),
                referenceDatumAfterChecking.lineNumber(),
                String.join(" - ", dataImporterContext().getNaturalKeyColumnsImportHeaders()));
        return Ltree.fromSql(naturalKeyAsString);
    }

    @Override
    public Ltree getHierarchicalKey(Ltree naturalKey, final DataDatum referenceDatum, ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        Ltree naturalKey1 = DataTransformer.getHierarchicalNodeFromNatural(naturalKey.getSql(), dataImporterContext.getRefType());
        return dataImporterContext.newHierarchicalKey(naturalKey1, referenceDatumAfterChecking.referenceDatumAfterChecking());
    }

    @Override
    public List<ReferenceDatumAfterChecking> testHasParent(Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey, RecursionStrategy recursionStrategy, ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        return List.of(referenceDatumAfterChecking);
    }
}