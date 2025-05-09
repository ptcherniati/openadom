package fr.inra.oresing.domain.data.deposit.recursion;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ListMultimap;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.MissingParentLineValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public record WithRecursion(
        DataImporterContext dataImporterContext,
        Map<DataValue.LineIdentityColumnName, Ltree> parentReferenceMap) implements RecursionStrategy {
    /**
     * When we have the hierarchical key, we can recover the natural key as the leaf of the ltree.
     * In this case you must remove the reference to the data type "[^\\.][a-z][a-z]*K"
     */
    //public static final Function<Ltree, Ltree> fromNaturalKey = nk -> Ltree.fromSql(nk.getSql().replaceAll("[^\\.][a-z][a-z]*K", ""));
    public WithRecursion(final DataImporterContext dataImporterContext) {
        this(dataImporterContext, new HashMap<>());
    }

    public static Function<Ltree, Ltree> toNaturalKey(String dataname) {
        return nk -> Ltree.fromSql("%sK%s".formatted(dataname, nk.getSql()));
    }

    /*
         to construct the natural key we concatenate the values of the key columns.
         - if the column is a repository type column, its value is an ltree. We take the last element of this ltree as part of the key
         - otherwise we escape the value
         - date values receive special treatment
      */
    @Override
    public Ltree computeNaturalKey(ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        Function<String, String> nullOrEmptyToNull = partialKey -> Strings.isNullOrEmpty(partialKey) ? Ltree.NULL_KEY : partialKey;
        Function<DataColumn, String> toEscapedValueFromColumnRegardingColumnIsReferenceType = dataColumn -> getEscapedValueFromColumnRegardingColumnIsReferenceType(dataColumn, referenceDatumAfterChecking.referenceDatumAfterChecking());
        String naturalKey = dataImporterContext().getNaturalKeyColumns().stream()
                .map(DataColumn::new)
                .map(toEscapedValueFromColumnRegardingColumnIsReferenceType)
                .map(nullOrEmptyToNull)
                .collect(Collectors.joining(DataImporterContext.COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR));
        Preconditions.checkState(!naturalKey.isEmpty(), ExceptionMessage.NULL_NATURAL_KEY.toMessage(), referenceDatumAfterChecking.lineNumber(), String.join(" - ", dataImporterContext().getNaturalKeyColumnsImportHeaders()));
        return Ltree.fromSql(naturalKey/*.replaceAll("^%s__".formatted(Ltree.NULL_KEY) "")*/);
    }

    String getEscapedValueFromColumnRegardingColumnIsReferenceType(DataColumn dataColumn, DataDatum referenceDatum) {
        boolean isReferenceColumn = dataImporterContext().getLineCheckers().stream()
                .filter(lineChecker -> lineChecker.target().equals(dataColumn))
                .map(LineChecker::underlyingType)
                .anyMatch(ReferenceType.class::isInstance);
        String dataValue = referenceDatum.get(dataColumn).toJsonForDatabase().toString();
        if (Strings.isNullOrEmpty(dataValue)) {
            return "";
        }
        if (isReferenceColumn) {
            return Ltree.fromSql(dataValue).last().getSql();
        } else {
            return Ltree.fromUnescapedString(dataValue).getSql();
        }
    }

    @Override
    public Ltree getHierarchicalKey(final Ltree naturalKey, final DataDatum referenceDatum, ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        Optional<DataValue.LineIdentityColumnName> registerId = dataImporterContext().getAfterPreloadReferenceUuids().keySet()
                .stream()
                .filter(lineIdentityColumnName -> lineIdentityColumnName.naturalKey().equals(naturalKey))
                .findFirst();
        if (registerId.isPresent()) {
            return registerId.get().hierarchicalKey();
        }
        String parentType = dataImporterContext()
                .getDataDescription()
                .findParentDescription(dataImporterContext().getRefType())
                .map(ComponentDescription::checker)
                .map(ReferenceChecker.class::cast)
                .map(ReferenceChecker::refType)
                .orElse(null);
        Optional<Ltree> parentValue = dataImporterContext()
                .getDataDescription()
                .findParentDescription(dataImporterContext().getRefType())
                .map(ComponentDescription::componentKey)
                .map(DataColumn::new)
                .map(referenceDatumAfterChecking.referenceDatumAfterChecking()::get)
                .map(DataColumnValue::toJsonForDatabase)
                .map(Object::toString)
                .map(Ltree::fromSql)
                .map(toNaturalKey(parentType));
        Ltree parentRecursiveValue =
                dataImporterContext()
                        .getDataDescription().componentDescriptions().values()
                        .stream()
                        .map(ComponentDescription::checker)
                        .filter(ReferenceChecker.class::isInstance)
                        .map(ReferenceChecker.class::cast)
                        .filter(ReferenceChecker::isRecursive)
                        .findAny()
                        .map(ReferenceChecker::componentKey)
                        .map(DataColumn::new)
                        .map(referenceDatum.values()::get)
                        .map(DataColumnValue::getValuesToCheck)
                        .filter(ReferenceType.class::isInstance)
                        .map(ReferenceType.class::cast)
                        .map(ReferenceType::getValue)
                        .map(this::recursiveNodeHierarchicalKey)
                        .orElse(null);
        Ltree hierarchicalKey = recursiveNodeHierarchicalKey(naturalKey);
        if (parentRecursiveValue != null) {
            hierarchicalKey = Ltree.join(parentRecursiveValue, hierarchicalKey);
        }
        if (parentValue.isPresent()) {
            hierarchicalKey = Ltree.join(parentValue.get(), hierarchicalKey);
        }
        return hierarchicalKey;
    }

    private Ltree recursiveNodeHierarchicalKey(final Ltree naturalKey) {
        return Ltree.fromSql("%sK%s".formatted(dataImporterContext().getRefType(), naturalKey));
    }

    @Override
    public List<ReferenceDatumAfterChecking> testHasParent(
            Function<ReferenceDatumAfterChecking, KeysAndReferenceDatumAfterChecking> buildKey,
            RecursionStrategy recursionStrategy,
            ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        KeysAndReferenceDatumAfterChecking keys = buildKey.apply(referenceDatumAfterChecking);
        Optional<UUID> knownId = dataImporterContext().getKnownId(keys.naturalKey());
        DataValue.LineIdentityColumnName key = new DataValue.LineIdentityColumnName(keys.naturalKey(), keys.hierarchicalKey());
        if (knownId.isEmpty()) {
            dataImporterContext().getAfterPreloadReferenceUuids().put(key, UUID.randomUUID());
            dataImporterContext().getKnownId(keys.naturalKey());
        }
        return List.of(referenceDatumAfterChecking);
    }

    /**
     * Pour une ligne passée, calcule la clé naturelle composite de cette ligne.
     * <p>
     * Il s'agit d'aller lire les différentes colonnes qui composent la clé, de joindre le tout et de gérer
     * l'échappement.
     */
    private DataValue.LineIdentityColumnName computeIdentityKey(final DataDatum referenceDatum) {
        final String naturalKeyAsString = dataImporterContext.getKeyColumns().stream()
                .map(referenceColumn -> {
                    final DataColumnValue referenceColumnValue = referenceDatum.get(referenceColumn);
                    Preconditions.checkState(referenceColumnValue instanceof DataColumnSingleValue, "dans le référentiel " + dataImporterContext.getRefType() + " la colonne " + referenceColumn + " est utilisée comme clé. Par conséquent, il ne peut pas y avoir une valeur multiple.");
                    return referenceColumnValue;
                })
                .map(DataColumnSingleValue.class::cast)
                .map(DataColumnSingleValue::getValue)
                .map(Object::toString)
                .map(s -> Strings.isNullOrEmpty(s) ? Ltree.NULL_KEY : s)
                .map(Ltree::escapeToLabel)
                .collect(Collectors.joining(DataImporterContext.getCompositeNaturalKeyComponentsSeparator()));
        Ltree naturalKey = Ltree.fromSql(naturalKeyAsString);
        return new DataValue.LineIdentityColumnName(naturalKey, naturalKey); //TODO
    }

    /**
     * Si on a détecté des lignes qui font référence à un parent mais que celui-ci n'existe pas, on lève une exception
     *
     * @param missingParentReferences pour chaque parent manquant, les lignes du CSV où il est mentionné
     */
    private void checkMissingParentReferencesIsEmpty(final ListMultimap<Ltree, Long> missingParentReferences) {
        final ReportErrors reportErrors = new ReportErrors(dataImporterContext.getJsonRowMapper());
        missingParentReferences.entries().stream()
                .map(entry -> {
                    final Ltree missingParentReference = entry.getKey();
                    final Long lineNumber = entry.getValue();
                    final ValidationCheckResult validationCheckResult =
                            new MissingParentLineValidationCheckResult(lineNumber, dataImporterContext.getRefType(), missingParentReference, dataImporterContext().getAfterPreloadReferenceUuids().keySet());
                    return validationCheckResult.getValidations().stream()
                            .map(validationCheckResult1 -> new CsvRowValidationCheckResult(validationCheckResult1, lineNumber))
                            .collect(Collectors.toList());
                })
                .flatMap(List::stream)
                .forEach(reportErrors::add);
        InvalidDatasetContentException.checkErrorsIsEmpty(reportErrors);
    }
}