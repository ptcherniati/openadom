package fr.inra.oresing.domain.data.deposit.transformation;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.ReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.internationalization.InternationalizationDisplay;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class DataTransformer {
    public DataImporter getDataImporter() {
        return dataImporter;
    }

    private final DataImporter dataImporter;

    public DataTransformer(DataImporter dataImporter) {
        this.dataImporter = dataImporter;
    }

    public static Ltree getHierarchicalNodeFromNatural(final String naturalKey, final String refType) {
        final Ltree escapedNaturalKey = Ltree.fromUnescapedString(naturalKey);
        final Ltree type = Ltree.fromUnescapedString(refType);
        final String naturalKeyToString = "%1$s%3$s%2$s".formatted(type, escapedNaturalKey, DataImporter.HIERARCHICALKEY_SEPARATOR);
        return Ltree.fromSql(naturalKeyToString);
    }

    public ImmutableSet<LineChecker<? extends FieldType>> buildLineCheckers(Map<DataColumn, DataColumnValue> constantColumnsValues) {
        return dataImporter.getCsvReader().buildLineCheckers(constantColumnsValues);
    }

    public RowWithReferenceDatum computeComputedColumns(final RowWithReferenceDatum rowWithReferenceDatum) {
        final DataDatum rowWithDefaults = new DataDatum();
        final DataDatum rowWithValues = DataDatum.copyOf(rowWithReferenceDatum.referenceDatum());
        dataImporter.getDataImporterContext().getColumns().stream()
                .filter(column -> column.getComputedValueUsage() != ComputedValueUsage.NOT_COMPUTED)
                .forEach(column -> {
                    final DataColumn referenceColumn = column.getReferenceColumn();
                    final Optional<DataColumnValue> evaluate = column.computeValue(rowWithReferenceDatum.referenceDatum());
                    evaluate.ifPresent(presentEvaluate -> {
                        if (column.getComputedValueUsage() == ComputedValueUsage.USE_COMPUTED_VALUE) {
                            rowWithValues.put(referenceColumn, presentEvaluate);
                        } else if (column.getComputedValueUsage() == ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE) {
                            rowWithDefaults.put(referenceColumn, presentEvaluate);
                        } else {
                            throw ComputedValueUsage.getError(column.getComputedValueUsage());
                        }
                    });
                });
        rowWithDefaults.putAll(rowWithValues);
        return new RowWithReferenceDatum(rowWithReferenceDatum.lineNumber(), rowWithReferenceDatum.patternColumnName(), rowWithDefaults, rowWithReferenceDatum.refsLinkedTo());
    }

    /**
     * Associe à chaque ligne une clé naturelle (peut-être composite ?) et une clé hiérarchique.
     */
    public KeysAndReferenceDatumAfterChecking computeKeys(final ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        DataDatum referenceDatum = referenceDatumAfterChecking.referenceDatumAfterChecking();
        Ltree naturalKey = dataImporter.getRecursionStrategy().computeNaturalKey(referenceDatumAfterChecking);
        DataDatum datumForColumnsInKey = new DataDatum(
                referenceDatum.values().entrySet()
                        .stream().filter(entry ->
                                dataImporter.getDataImporterContext().getNaturalKeyColumns().contains(entry.getKey().column())
                        )
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        Ltree hierarchicalKey = dataImporter.getRecursionStrategy().getHierarchicalKey(naturalKey, datumForColumnsInKey, referenceDatumAfterChecking);
        return new KeysAndReferenceDatumAfterChecking(
                referenceDatumAfterChecking,
                naturalKey,
                hierarchicalKey);
    }

    /**
     * Transforme une ligne de données en une entité prête à être sauvée en base de données.
     */
    public DataValue toEntity(final KeysAndReferenceDatumAfterChecking keysAndReferenceDatumAfterChecking, final UUID fileId, ReportErrors errors) {
        ReferenceDatumAfterChecking referenceDatumAfterChecking = keysAndReferenceDatumAfterChecking.referenceDatumAfterChecking();
        DataDatum referenceDatum = referenceDatumAfterChecking.referenceDatumAfterChecking();
        Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();

        DataValue e = new DataValue();
        Ltree naturalKey = keysAndReferenceDatumAfterChecking.naturalKey();
        dataImporter.getDataImporterContext().getKnownId(naturalKey)
                .ifPresent(e::setId);
        referenceDatum.putAll(InternationalizationDisplay.getDisplaysName(dataImporter.getDataImporterContext(), referenceDatum));
        referenceDatum.putAll(InternationalizationDisplay.getDisplaysDescription(dataImporter.getDataImporterContext(), referenceDatum));

        final String patternColumnName = referenceDatumAfterChecking.patternColumnName();
        dataImporter.getDataImporterContext().getIdForSameHierarchicalKeyInDatabase(hierarchicalKey)
                .ifPresent(e::setId);

        Authorization lineAuthorization = dataImporter.getConfigurationSi().getLineAuthorization(referenceDatum, referenceDatumAfterChecking.lineNumber(), errors);

        e.setPatternColumnName(patternColumnName);
        e.setBinaryFile(fileId);
        e.setReferenceType(dataImporter.getDataImporterContext().getRefType());
        e.setHierarchicalKey(hierarchicalKey);
        e.setRefsLinkedTo(referenceDatumAfterChecking.refsLinkedTo());
        e.setAuthorization(lineAuthorization);
        e.setNaturalKey(naturalKey);
        e.setApplication(dataImporter.getDataImporterContext().getApplication().getId());
        e.setRefValues(referenceDatum);
        return e;
    }
}