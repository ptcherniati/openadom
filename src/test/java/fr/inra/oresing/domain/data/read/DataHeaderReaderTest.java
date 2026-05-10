package fr.inra.oresing.domain.data.read;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.deposit.context.ContextConstants;
import fr.inra.oresing.domain.data.deposit.context.column.PatternColumnFactory;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.domain.data.deposit.BuildColumns;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Tag("SUITE")
@Tag("domain.model")
class DataHeaderReaderTest {
    Iterator<CSVRecord> lineIterator;
    DataHeaderReader reader;
    StandardDataDescription dataDescription;

    private static void test(final DataHeaderReader reader) {
        final PublishContext publishContext = reader.publishContextBuilder().build();
        testHeaderRow(publishContext.headerInfos().headerRow());
        testPreHeaderRows(publishContext.headerInfos().preHeaderRow());
        testPostHeaderRows(publishContext.headerInfos().postHeaderRow());
        testDataDatum(reader.constantValues());
    }

    private static void testDataDatum(final DataDatum constants) {
        Assertions.assertEquals("bassin_versant", constants.get(new DataColumn("dat_type_site"))
                .toJsonForFrontend()
                .toString());
        Assertions.assertEquals("hesse", constants.get(new DataColumn("dat_site"))
                .toJsonForFrontend()
                .toString());
        Assertions.assertEquals("20/01/2014", constants.get(new DataColumn("dat_start_date"))
                .toJsonForFrontend()
                .toString());
        Assertions.assertEquals("30/01/2014", constants.get(new DataColumn("dat_end_date"))
                .toJsonForFrontend()
                .toString());
    }

    private static void testPostHeaderRows(final List<List<String>> postHeaderRows) {
        Assertions.assertEquals("[20/01/2014,],[30/01/2014]", postHeaderRows.stream()
                .map(l -> l.stream().collect(Collectors.joining(",", "[", "]")))
                .collect(Collectors.joining(",")));
    }

    private static void testPreHeaderRows(final List<List<String>> preHeaderRows) {
        Assertions.assertEquals("[type de site,bassin_versant,],[site,hesse,],[comment,uncommentaire,]", preHeaderRows.stream()
                .map(l -> l.stream().collect(Collectors.joining(",", "[", "]")))
                .collect(Collectors.joining(",")));
    }

    private static void testHeaderRow(final List<String> headerRows) {
        Assertions.assertEquals("dat_date,dat_heure,SMP_20_1,SMP_20_2,SMP_30_1", String.join(",", headerRows));
    }

    @BeforeEach
    public void init() throws IOException {
        final DataDatum constants = new DataDatum();
        BinaryFileDataset binaryFileDataset = new BinaryFileDataset();
        binaryFileDataset.setRequiredAuthorizations(Map.of("dat_site", List.of(Ltree.fromSql("hesse"))));
        FileOrUUID fileOrUUID = new FileOrUUID(
                null,
                binaryFileDataset,
                false
        );

        final PublishContext.PublishContextBuilder publishContextBuilder = new PublishContext.PublishContextBuilder(null, "", fileOrUUID, null);


        final URL url = com.google.common.io.Resources.getResource("data/configuration/data.result.example.json");
        final String json = Resources.toString(url, StandardCharsets.UTF_8);
        dataDescription = new JsonRowMapper<>().readValue(
                new JsonRowMapper<>().toJson(new JsonRowMapper<>()
                        .readValue(json, JsonNode.class)
                        .findPath("t_data_dat")),
                StandardDataDescription.class
        );

        final String file = """
                type de site;bassin_versant;
                site;hesse;
                comment;uncommentaire;
                dat_date;dat_heure;SMP_20_1;SMP_20_2;SMP_30_1
                20/01/2014;
                30/01/2014
                21/01/2014;03:52:00;12.3;52.1;32.0""";
        final InputStream csv = new ByteArrayInputStream(file.getBytes());
        final CSVFormat csvFormat = CSVFormat.Builder.create(CSVFormat.DEFAULT)
                .setDelimiter(dataDescription.separator())
                .setSkipHeaderRecord(true)
                .get();

        final CSVParser csvParser = CSVParser.parse(csv, StandardCharsets.UTF_8, csvFormat);
        lineIterator = csvParser.iterator();
        final AsynchroneFileImporterContext dataImporterContext = Mockito.mock(AsynchroneFileImporterContext.class);
        final ContextConstants contextConstants = Mockito.mock(ContextConstants.class);
        final BuildColumns buildColumns = Mockito.mock(BuildColumns.class);
        final PatternColumnFactory patternColumnFactory = Mockito.mock(PatternColumnFactory.class);
        Mockito.when(dataImporterContext.contextConstants()).thenReturn(contextConstants);
        Mockito.when(contextConstants.dataConfiguration()).thenReturn(dataDescription);
        Mockito.when(buildColumns.expectedHeaders()).thenReturn(ImmutableSet.of("dat_date", "dat_heure", "SMP_20_1", "SMP_20_2", "SMP_30_1"));
        Mockito.when(buildColumns.mandatoryHeaders()).thenReturn(ImmutableSet.of("dat_date", "dat_heure"));
        Mockito.when(dataImporterContext.buildColumns()).thenReturn(buildColumns);
        Mockito.when(buildColumns.patternColumnFactory()).thenReturn(patternColumnFactory);

        reader = new DataHeaderReader(
                buildColumns,
                publishContextBuilder,
                dataDescription
        );
    }

    @Test
    void readHeader() {
        reader.readHeader(lineIterator);
        test(reader);
    }
}