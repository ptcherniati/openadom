package fr.inra.oresing.domain.data.read;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.JsonRowMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.Charsets;
import org.junit.Assert;
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
class DataHeaderReaderTest {
    Iterator<CSVRecord> lineIterator;
    DataHeaderReader reader;
    StandardDataDescription dataDescription;

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
        final CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(dataDescription.separator())
                .withSkipHeaderRecord();
        final CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
        lineIterator = csvParser.iterator();
        final DataImporterContext dataImporterContext = Mockito.mock(DataImporterContext.class);
        Mockito.when(dataImporterContext.getDataDescription()).thenReturn(dataDescription);
        Mockito.when(dataImporterContext.getExpectedHeaders()).thenReturn(ImmutableSet.of("dat_date","dat_heure","SMP_20_1","SMP_20_2","SMP_30_1"));
        Mockito.when(dataImporterContext.getMandatoryHeaders()).thenReturn(ImmutableSet.of("dat_date","dat_heure"));
        reader = new DataHeaderReader(constants, dataImporterContext, publishContextBuilder);
    }

    @Test
    void readHeader() {
        reader.readHeader(lineIterator);
        test(reader);
    }

    private static void test(final DataHeaderReader reader) {
        final PublishContext publishContext = reader.publishContextBuilder().build();
        testHeaderRow(publishContext.headerInfos().headerRow());
        testPreHeaderRows(publishContext.headerInfos().preHeaderRow());
        testPostHeaderRows(publishContext.headerInfos().postHeaderRow());
        testDataDatum(reader.constantValues());
    }

    private static void testDataDatum(final DataDatum constants) {
        Assert.assertEquals(
                "bassin_versant",
                constants.get(new DataColumn("dat_type_site"))
                        .toJsonForFrontend()
                        .toString()
        );
        Assert.assertEquals(
                "hesse",
                constants.get(new DataColumn("dat_site"))
                        .toJsonForFrontend()
                        .toString()
        );
        Assert.assertEquals(
                "20/01/2014",
                constants.get(new DataColumn("dat_start_date"))
                        .toJsonForFrontend()
                        .toString()
        );
        Assert.assertEquals(
                "30/01/2014",
                constants.get(new DataColumn("dat_end_date"))
                        .toJsonForFrontend()
                        .toString()
        );
    }

    private static void testPostHeaderRows(final List<List<String>> postHeaderRows) {
        Assert.assertEquals(
                "[20/01/2014,],[30/01/2014]",
                postHeaderRows.stream()
                        .map(l->l.stream().collect(Collectors.joining(",", "[","]")))
                        .collect(Collectors.joining(","))
        );
    }

    private static void testPreHeaderRows(final List<List<String>> preHeaderRows) {
        Assert.assertEquals(
                "[type de site,bassin_versant,],[site,hesse,],[comment,uncommentaire,]",
                preHeaderRows.stream()
                        .map(l->l.stream().collect(Collectors.joining(",", "[","]")))
                        .collect(Collectors.joining(","))
        );
    }

    private static void testHeaderRow(final List<String> headerRows) {
        Assert.assertEquals(
                "dat_date,dat_heure,SMP_20_1,SMP_20_2,SMP_30_1",
                String.join(",", headerRows)
        );
    }
}