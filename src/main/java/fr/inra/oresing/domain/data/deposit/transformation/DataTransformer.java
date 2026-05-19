package fr.inra.oresing.domain.data.deposit.transformation;

import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.data.deposit.configuration.ConfigurationSi;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.deposit.recursion.RecursionStrategy;
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

    private final AsynchroneFileImporterContext dataImporterContext;
    private final RecursionStrategy recursionStrategy;
    private final ConfigurationSi configurationSi;

    public DataTransformer(AsynchroneFileImporterContext dataImporterContext, RecursionStrategy recursionStrategy) {
        this.dataImporterContext = dataImporterContext;
        this.recursionStrategy = recursionStrategy;
        this.configurationSi = new ConfigurationSi(dataImporterContext);
    }

    public static Ltree getHierarchicalNodeFromNatural(final String naturalKey, final String refType) {
        final Ltree escapedNaturalKey = Ltree.fromUnescapedString(naturalKey);
        final Ltree type = Ltree.fromUnescapedString(refType);
        final String naturalKeyToString = "%1$s%3$s%2$s".formatted(type, escapedNaturalKey, DataImporter.HIERARCHICALKEY_SEPARATOR);
        return Ltree.fromSql(naturalKeyToString);
    }

    public RowWithReferenceDatum computeComputedColumns(final RowWithReferenceDatum rowWithReferenceDatum) {
        final DataDatum rowWithValues = DataDatum.copyOf(rowWithReferenceDatum.referenceDatum());
        dataImporterContext.buildColumns().columns().stream()
                .filter(column -> column.getComputedValueUsage() != ComputedValueUsage.NOT_COMPUTED)
                .forEach(column -> {
                    final DataColumn referenceColumn = column.getReferenceColumn();
                    final Optional<DataColumnValue> evaluate = column.computeValue(rowWithReferenceDatum.referenceDatum());
                    evaluate.ifPresent(presentEvaluate -> {
                        if (column.getComputedValueUsage() == ComputedValueUsage.USE_COMPUTED_VALUE) {
                            rowWithValues.put(referenceColumn, presentEvaluate);
                        } else if (column.getComputedValueUsage() == ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE) {
                            rowWithValues.put(referenceColumn, presentEvaluate);
                        } else {
                            throw ComputedValueUsage.getError(column.getComputedValueUsage());
                        }
                    });
                });
        return new RowWithReferenceDatum(rowWithReferenceDatum.lineNumber(), rowWithReferenceDatum.patternColumnName(), rowWithValues, rowWithReferenceDatum.refsLinkedTo());
    }

    /**
     * Associe à chaque ligne une clé naturelle (peut-être composite ?) et une clé hiérarchique.
     */
    public KeysAndReferenceDatumAfterChecking computeKeys(final ReferenceDatumAfterChecking referenceDatumAfterChecking) {
        DataDatum referenceDatum = referenceDatumAfterChecking.referenceDatumAfterChecking();
        Ltree naturalKey = recursionStrategy.computeNaturalKey(referenceDatumAfterChecking);
        DataDatum datumForColumnsInKey = new DataDatum(
                referenceDatum.values().entrySet()
                        .stream().filter(entry ->
                                dataImporterContext.getNaturalKeyColumns().contains(entry.getKey().column())
                        )
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        Ltree hierarchicalKey = recursionStrategy.getHierarchicalKey(naturalKey, datumForColumnsInKey, referenceDatumAfterChecking);
        return new KeysAndReferenceDatumAfterChecking(
                referenceDatumAfterChecking,
                naturalKey,
                hierarchicalKey,
                referenceDatumAfterChecking.patternColumnName());
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
        final String patternColumnName = referenceDatumAfterChecking.patternColumnName();
        dataImporterContext.getKnownId(naturalKey, patternColumnName)
                .ifPresent(e::setId);
        referenceDatum.putAll(InternationalizationDisplay.getDisplaysName(dataImporterContext, referenceDatum));
        referenceDatum.putAll(InternationalizationDisplay.getDisplaysDescription(dataImporterContext, referenceDatum));

        dataImporterContext.getIdForSameHierarchicalKeyInDatabase(hierarchicalKey, patternColumnName)
                .ifPresent(e::setId);

        Authorization lineAuthorization = configurationSi.getLineAuthorization(referenceDatum, referenceDatumAfterChecking.lineNumber(), errors);

        e.setPatternColumnName(patternColumnName);
        e.setBinaryFile(fileId);
        e.setReferenceType(dataImporterContext.contextConstants().refType());
        e.setHierarchicalKey(hierarchicalKey);
        e.setRefsLinkedTo(referenceDatumAfterChecking.refsLinkedTo());
        e.setAuthorization(lineAuthorization);
        e.setNaturalKey(naturalKey);
        e.setApplication(dataImporterContext.contextConstants().application().getId());
        e.setRefValues(referenceDatum);
        return e;
    }
}