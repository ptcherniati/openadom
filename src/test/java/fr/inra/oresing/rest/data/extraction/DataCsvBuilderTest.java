package fr.inra.oresing.rest.data.extraction;

import com.google.common.io.Resources;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.data.DataService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockitoAnnotations;
import reactor.core.publisher.Flux;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

@DisplayName("test de csv builder")
public class DataCsvBuilderTest {
    public static final String DATA_NAME = "pem";
    public static final String DATA_ZIP_OUT = "/tmp/testBuildShouldReturnUUIDsfromDataOut.zip";
    static Application application;
    static DownloadDatasetQuery downloadDataSetQueryNoFilter;
    static DownloadDatasetQuery downloadDatasetQueryAdvancedSearch;
    static List<DataRow> dataRows;
    static Flux<DataRow> datasFlux;
    @BeforeEach
    void setUp(){
        MockitoAnnotations.openMocks(this);
    }

    @BeforeAll
    static void getConfigurationFile() throws IOException {
        String applicationString = Resources.toString(
                Resources.getResource("./data/application/monsore.json"),
                StandardCharsets.UTF_8
        );
        String downloadDataSetQueryNoFilterString = Resources.toString(
                Resources.getResource("./data/application/downloadDatasetQueryNoFilter.json"),
                StandardCharsets.UTF_8
        );
        String downloadDatasetQueryAdvancedSearchString = Resources.toString(
                Resources.getResource("./data/application/downloadDatasetQueryAdvancedSearch.json"),
                StandardCharsets.UTF_8
        );
        String dataRowsString = Resources.toString(
                Resources.getResource("./data/application/data.json"),
                StandardCharsets.UTF_8
        );
        application = new JsonRowMapper<Application>().readValue(applicationString, Application.class);
        fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery localDownloadDatasetQuery = new JsonRowMapper<fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery>().toObject(downloadDataSetQueryNoFilterString, fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.class);
        localDownloadDatasetQuery.setApplication(application);
        localDownloadDatasetQuery.setDataName(DATA_NAME);
        downloadDataSetQueryNoFilter = fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.build(localDownloadDatasetQuery);
        localDownloadDatasetQuery = new JsonRowMapper<fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery>().toObject(downloadDatasetQueryAdvancedSearchString, fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.class);
        localDownloadDatasetQuery.setApplication(application);
        localDownloadDatasetQuery.setDataName(DATA_NAME);
        downloadDatasetQueryAdvancedSearch = fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.build(localDownloadDatasetQuery);
        dataRows = new JsonRowMapper<Application>().readValue(dataRowsString, List.class);
        datasFlux = Flux.fromStream(dataRows.stream());
    }

    record DownloadDatasetQueryArguments(String description, DownloadDatasetQuery query) {
    }

    static Stream<DownloadDatasetQueryArguments> provideDownloadDatasetQuery() {

        return Stream.of(/*
                new DownloadDatasetQueryArguments(
                        "queryByNaturalKey",
                        initialize(mock(DownloadDatasetQueryByNaturalKey.class, "queryByNaturalKey"))
                ),
                new DownloadDatasetQueryArguments(
                        "queryByRowId",
                        initialize(mock(DownloadDatasetQueryByRowId.class, "queryByRowId"))
                ),*/
                new DownloadDatasetQueryArguments(
                        "queryAdvancedSearch",
                        downloadDatasetQueryAdvancedSearch
                ),
                new DownloadDatasetQueryArguments(
                        "queryNoFilter",
                        downloadDataSetQueryNoFilter
                )
        );
    }

    @ParameterizedTest(name = "{0} - test build")
    @MethodSource("provideDownloadDatasetQuery")
    @DisplayName("On peut builder")
    public void testBuildShouldReturnUUIDsfromData(DownloadDatasetQueryArguments arguments) throws IOException {

        DataService dataService= mock(DataService.class);
        doReturn(datasFlux).when(dataService).findDataFlux(any(DownloadDatasetQuery.class));
        final DataCsvBuilder builder = DataCsvBuilder.getDataCsvBuilder((applicationNameOrId, referenceType) -> null)
                .withDownloadDatasetQuery(arguments.query())
                .withReferenceService(dataService)
                .addDatas(datasFlux);
        try (FileOutputStream fis = new FileOutputStream(DATA_ZIP_OUT);
             BufferedOutputStream bos = new BufferedOutputStream(fis);
             ZipOutputStream zipOutPut = new ZipOutputStream(bos);) {
            builder.withOutputStream(zipOutPut);
            var result = builder.build("test-%s.csv");
            assertNotNull(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}