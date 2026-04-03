package fr.inra.oresing.rest.data.extraction;

import com.opencsv.CSVWriter;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationComponent;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationData;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.data.UUIDsfromData;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.read.query.ComponentOrderBy;
import fr.inra.oresing.domain.data.read.query.ComponentOrderByForExport;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.persistence.AdditionalFileRepository;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.rest.data.DataService;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DataCsvBuilder {
    private static final Logger log = LoggerFactory.getLogger(DataCsvBuilder.class);
    private DownloadDatasetQuery downloadDatasetQuery;

    private DataRepository dataRepository;

    private Flux<DataRow> datas;
    private Path zipRepository;

    public DataCsvBuilder() {
        super();
    }

    public static DataCsvBuilder getDataCsvBuilder(final BiFunction<String, String, AsynchroneFileImporterContext> referenceImporterContextBuilder) {
        return new DataCsvBuilder();
    }

    private static DataRow addRefsLinkedTo(DataRow dataRow, UUIDsfromData uuidsfromData) {
        Optional.ofNullable(dataRow)
                .map(DataRow::refsLinked)
                .ifPresent(refsLinkeds -> refsLinkeds.forEach(uuidsfromData::addRefsLinkedTo));
        return dataRow;
    }

    public DataCsvBuilder withDownloadDatasetQuery(final DownloadDatasetQuery downloadDatasetQuery) {
        this.downloadDatasetQuery = downloadDatasetQuery;
        return this;
    }

    public DataCsvBuilder onRepositories(DataRepository dataRepository, AdditionalFileRepository additionalFileRepository) {
        this.dataRepository = dataRepository;
        return this;
    }

    public DataCsvBuilder withReferenceService(final DataService referenceService) {

        return this;
    }

    public DataCsvBuilder addDatas(final Flux<DataRow> datas) {
        this.datas = datas;
        return this;
    }

    public UUIDsfromData build(String fileNamePattern) throws IOException {
        Optional<StandardDataDescription> data = downloadDatasetQuery.application()
                .findData(downloadDatasetQuery.dataName());
        final StandardDataDescription dataDescription = data
                .orElseThrow(() -> new IllegalStateException("can't find application %s".formatted(downloadDatasetQuery.dataName())));
        final String relativeFileName = String.format(fileNamePattern, downloadDatasetQuery.dataName());
        Path outputFile = zipRepository.resolve(relativeFileName);
        Files.createDirectories(outputFile.getParent());
        String language = "fr";
        UUIDsfromData uuiDsfromData = null;
        try (OutputStream outputStream = Files.newOutputStream(outputFile)) {
            uuiDsfromData = new UUIDsfromData();
            language = downloadDatasetQuery.getLanguage();
            uuiDsfromData = buildDataCsv(outputStream, language, dataDescription, downloadDatasetQuery.horizontalDisplay());
        } catch (final Exception e) {
            // Nom du fichier d’erreur à générer (à côté du CSV)
            String errorFileName = String.format("error-%s.txt", downloadDatasetQuery.dataName());
            Path errorFile = zipRepository.resolve(errorFileName);
            Files.createDirectories(errorFile.getParent());
            try (OutputStream errOutput = Files.newOutputStream(errorFile)) {
                switch (language) {
                    case "fr" -> errOutput.write("Une erreur s'est produite lors du téléchargement.".getBytes());
                    case "en" -> errOutput.write("An error occurred during download.".getBytes());
                    default -> errOutput.write("An error occurred during download.".getBytes());
                }
                errOutput.write("\n\n".getBytes());
                errOutput.write(e.getMessage().getBytes());
            }
        }
        return uuiDsfromData;
    }

    public UUIDsfromData buildDataCsv(OutputStream outputStream, String language, StandardDataDescription dataDescription, boolean horizontalDisplay) {
        final UUIDsfromData uuiDsfromData = new UUIDsfromData();
        Character separator = downloadDatasetQuery.application().findData(downloadDatasetQuery.dataName())
                .map(StandardDataDescription::separator)
                .orElse(';');
        BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), 1024);
        CSVWriter writer = new CSVWriter(bufferedWriter, separator, CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER, CSVWriter.DEFAULT_LINE_END);

        final Set<String> componentSelects = Optional.ofNullable(downloadDatasetQuery)
                .map(DownloadDatasetQuery::componentSelects)
                .filter(CollectionUtils::isNotEmpty)
                .or(() -> Optional.ofNullable(dataDescription)
                        .map(StandardDataDescription::componentDescriptions)
                        .map(Map::keySet))
                .orElseGet(Set::of);
        LinkedList<String> elementsToBeSortedInFirst = Optional.of(Objects.requireNonNull(downloadDatasetQuery))
                .map(DownloadDatasetQuery::componentOrderBy)
                .stream().flatMap(Set::stream)
                .map(ComponentOrderBy::componentKey)
                .collect(Collectors.toCollection(LinkedList::new));
        Map<String, Configuration.InternationalizedSortedColumn> internationalizedSortedColumns = downloadDatasetQuery.application().getConfiguration()
                .getInternationalizedSortedColumns(
                        downloadDatasetQuery.dataName(),
                        downloadDatasetQuery.getLanguage(),
                        elementsToBeSortedInFirst);
        try {
            Function<String, String> getInternationalizedHeader = componentName -> Optional.ofNullable(
                            downloadDatasetQuery.application()
                                    .getConfiguration().i18n())
                    .map(Internationalizations::getData)
                    .map(data -> data.get(downloadDatasetQuery.dataName()))
                    .map(InternationalizationData::getComponents)
                    .map(components -> components.get(componentName))
                    .map(InternationalizationComponent::getExportHeader)
                    .map(InternationalizationTitle::getTitle)
                    .map(title -> title.get(downloadDatasetQuery.getLocale())
                    ).orElse(componentName);
            Comparator<ComponentOrderByForExport> comparator = ComponentOrderByForExport.getComparator(dataDescription);
            DataCsvHeaderWriter dataCsvHeaderWriter = new DataCsvHeaderWriter(
                    writer,
                    comparator,
                    getInternationalizedHeader,
                    dataRepository,
                    dataDescription,
                    internationalizedSortedColumns,
                    horizontalDisplay
            );
            DataCsvRowBuilder dataCsvRowBuilder = new DataCsvRowBuilder(language, dataRepository, dataDescription, horizontalDisplay);
            datas
                    .map(dataCsvHeaderWriter::writeHeader)
                    .map(dataRow -> addRefsLinkedTo(dataRow, uuiDsfromData))
                    .map(dataRow -> dataCsvRowBuilder.getCsvRow(dataRow.refsLinked(), dataRow.values(), dataCsvHeaderWriter.orderedColumns()))
                    .doOnNext(csvRow -> {
                        try {
                            writer.writeNext(csvRow.toArray(new String[0]));
                        } catch (Exception e) {
                            log.error("Error writing CSV row", e);
                        }
                    })
                    .doOnComplete(() -> {
                        try {
                            writer.flush();
                            bufferedWriter.flush();
                        } catch (IOException e) {
                            log.error("Error flushing CSV writer", e);
                        }
                    })
                    .blockLast(); // Attendre que toutes les données soient traitées
        } catch (Exception e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
        return uuiDsfromData;
    }

    public DataCsvBuilder withZipRepository(final Path zipRepository) {
        this.zipRepository = zipRepository;
        return this;
    }

}