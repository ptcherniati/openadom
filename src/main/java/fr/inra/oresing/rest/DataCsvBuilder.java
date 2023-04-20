package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.model.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.persistence.AdditionalFileSearchHelper;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.util.CollectionUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DataCsvBuilder {
    private String dataType;
    private Application application;
    private DownloadDatasetQuery downloadDatasetQuery;
    private CheckerFactory checkerFactory;

    private OreSiRepository repo;
    private ReferenceService referenceService;
    private List<DataRow> datas;

    public DataCsvBuilder withDownloadDatasetQuery(DownloadDatasetQuery downloadDatasetQuery) {
        this.downloadDatasetQuery = downloadDatasetQuery;
        return this;
    }

    public DataCsvBuilder withApplication(Application application) {
        this.application = application;
        return this;
    }

    public DataCsvBuilder withDatatype(String dataType) {
        this.dataType = dataType;
        return this;
    }

    public DataCsvBuilder withCheckerFactory(CheckerFactory checkerFactory) {
        this.checkerFactory = checkerFactory;
        return this;
    }

    public DataCsvBuilder onRepositories(OreSiRepository repo) {
        this.repo = repo;
        return this;
    }

    public DataCsvBuilder withReferenceService(ReferenceService referenceService) {
        this.referenceService = referenceService;
        return this;
    }

    public DataCsvBuilder addDatas(List<DataRow> datas) {
        this.datas = datas;
        return this;
    }

    public static DataCsvBuilder getDataCsvBuilder() {
        return new DataCsvBuilder();
    }

    public byte[] build() {
        Configuration.FormatDescription format = downloadDatasetQuery.getApplication()
                .getConfiguration()
                .getDataTypes()
                .get(dataType)
                .getFormat();
        ImmutableMap<String, DownloadDatasetQuery.VariableComponentOrderBy> allColumns = ImmutableMap.copyOf(getExportColumns(format).entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> new DownloadDatasetQuery.VariableComponentOrderBy(e.getValue(), DownloadDatasetQuery.Order.ASC)
                )));
        ImmutableMap<String, DownloadDatasetQuery.VariableComponentOrderBy> columns;
        List<String> dateLineCheckerVariableComponentKeyIdList = checkerFactory.getLineCheckers(application, dataType).stream()
                .filter(ch -> ch instanceof DateLineChecker)
                .map(ch -> (DateLineChecker) ch)
                .map(ch -> ((VariableComponentKey) ch.getTarget()).getId())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(downloadDatasetQuery.getVariableComponentOrderBy())) {
            columns = allColumns;
        } else {
            columns = ImmutableMap.copyOf(downloadDatasetQuery.getVariableComponentOrderBy().stream()
                    .collect(Collectors.toMap(DownloadDatasetQuery.VariableComponentOrderBy::getId, k -> k)));
        }
        byte[] result = new byte[0];
        if (datas.size() > 0) {
            CSVFormat csvFormat = CSVFormat.DEFAULT
                    .withDelimiter(format.getSeparator())
                    .withSkipHeaderRecord();
            StringWriter out = new StringWriter();
            CSVPrinter csvPrinter = null;
            try {
                csvPrinter = new CSVPrinter(out, csvFormat);
                addDatasToZip(datas, columns, dateLineCheckerVariableComponentKeyIdList, csvPrinter);
                final Set<UUID> referencedUUID = getReferencesLinkedTo();
                final Map<String, List<ReferenceValue>> referencesValues = repo.getRepository(application.getId()).referenceValue().getLinkedReferenceValues(referencedUUID)
                        .stream()
                        .collect(Collectors.groupingBy(ReferenceValue::getReferenceType));
                final Set<UUID> datasIds = datas.stream().map(DataRow::getRowId).map(UUID::fromString).collect(Collectors.toSet());
                final List<AdditionalBinaryFile> additionalBinaryFiles = repo.getRepository(application.getId()).additionalBinaryFile()
                        .getAssociatedAdditionalFiles(datasIds)
                        .stream()
                        .collect(Collectors.toList());
                result = zipData(application, csvFormat, out.toString(), dataType, referencesValues, additionalBinaryFiles);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }


    private byte[] zipData(Application application,
                           CSVFormat csvFormat,
                           String data,
                           String dataName,
                           Map<String, List<ReferenceValue>> referencesValues,
                           List<AdditionalBinaryFile> additionalBinaryFiles) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(byteArrayOutputStream)) {
            ZipEntry zipEntry = new ZipEntry(String.format("%s.csv", dataName));
            zipOutputStream.putNextEntry(zipEntry);
            zipOutputStream.write(data.getBytes());
            zipOutputStream.closeEntry();
            if (referencesValues != null && !referencesValues.isEmpty()) {
                referencesValues.entrySet().stream()
                        .forEach(entry -> addEntry(application, csvFormat, zipOutputStream, entry.getKey(), entry.getValue()));
            }
            if (additionalBinaryFiles != null && !additionalBinaryFiles.isEmpty()) {
                new AdditionalFileSearchHelper().addAdditionalFilesToZip(additionalBinaryFiles, zipOutputStream, "additionalFiles/");
            }
        }
        return byteArrayOutputStream.toByteArray();
    }

    private void addEntry(Application application,
                          CSVFormat csvFormat,
                          ZipOutputStream zipOutputStream,
                          String referenceName,
                          List<ReferenceValue> references) {
        ZipEntry zipEntry = new ZipEntry(String.format("references/%s.csv", referenceName));
        if (references.size() > 0) {
            final ReferenceImporterContext referenceImporterContext = referenceService.getReferenceImporterContext(application.getName(), referenceName);
            final byte[] referenceToCsv = referenceImporterContext.buildReferenceCSV(references);
            try {
                zipOutputStream.putNextEntry(zipEntry);
                zipOutputStream.write(referenceToCsv);
                zipOutputStream.closeEntry();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void addDatasToZip(List<DataRow> datas,
                               ImmutableMap<String, DownloadDatasetQuery.VariableComponentOrderBy> columns,
                               List<String> dateLineCheckerVariableComponentKeyIdList,
                               CSVPrinter csvPrinter) {
        try {
            csvPrinter.printRecord(columns.keySet());
            for (DataRow dataRow : datas) {
                Map<String, Map<String, String>> record = dataRow.getValues();
                ImmutableList<String> rowAsRecord = columns.values().stream()
                        .map(variableComponentSelect -> {
                            Map<String, String> components = record.computeIfAbsent(variableComponentSelect.getVariable(), k -> Collections.emptyMap());
                            String value = components.getOrDefault(variableComponentSelect.getComponent(), "");
                            if (dateLineCheckerVariableComponentKeyIdList.contains(variableComponentSelect.variableComponentKey.getId())) {
                                value = DateLineChecker.sortableDateToFormattedDate(value);
                            }
                            return value;
                        })
                        .collect(ImmutableList.toImmutableList());
                csvPrinter.printRecord(rowAsRecord);
            }
        } catch (IOException e) {
            throw new SiOreIllegalArgumentException(
                    "IOException",
                    Map.of(
                            "message", e.getLocalizedMessage()
                    )
            );
            // throw new OreSiTechnicalException("erreur lors de la génération du fichier CSV", e);
        }
    }

    private Set<UUID> getReferencesLinkedTo() {
        return datas.stream()
                .map(DataRow::getRefsLinkedTo)
                .map(refs -> refs.values().stream()
                        .map(s -> s.values().stream()
                                .filter(l -> l != null && !l.isEmpty())
                                .flatMap(Set::stream)
                                .collect(Collectors.toList()))
                        .filter(l -> !l.isEmpty())
                        .flatMap(List::stream)
                        .collect(Collectors.toList()))
                .filter(l -> !l.isEmpty())
                .flatMap(List::stream)
                .collect(Collectors.toSet());
    }


    private ImmutableMap<String, VariableComponentKey> getExportColumns(Configuration.FormatDescription format) {
        ImmutableMap<String, VariableComponentKey> valuesFromStaticColumns = format.getColumns().stream()
                .collect(ImmutableMap.toImmutableMap(Configuration.ColumnBindingDescription::getHeader, Configuration.ColumnBindingDescription::getBoundTo));
        ImmutableMap<String, VariableComponentKey> valuesFromConstants = format.getConstants().stream()
                .collect(ImmutableMap.toImmutableMap(Configuration.HeaderConstantDescription::getExportHeader, Configuration.HeaderConstantDescription::getBoundTo));
        ImmutableMap<String, VariableComponentKey> valuesFromHeaderPatterns = format.getRepeatedColumns().stream()
                .flatMap(repeatedColumnBindingDescription -> repeatedColumnBindingDescription.getTokens().stream())
                .collect(ImmutableMap.toImmutableMap(Configuration.HeaderPatternToken::getExportHeader, Configuration.HeaderPatternToken::getBoundTo));
        ImmutableMap<String, VariableComponentKey> valuesFromRepeatedColumns = format.getRepeatedColumns().stream()
                .collect(ImmutableMap.toImmutableMap(Configuration.RepeatedColumnBindingDescription::getExportHeader, Configuration.RepeatedColumnBindingDescription::getBoundTo));
        return ImmutableMap.<String, VariableComponentKey>builder()
                .putAll(valuesFromStaticColumns)
                .putAll(valuesFromConstants)
                .putAll(valuesFromHeaderPatterns)
                .putAll(valuesFromRepeatedColumns)
                .build();
    }
}