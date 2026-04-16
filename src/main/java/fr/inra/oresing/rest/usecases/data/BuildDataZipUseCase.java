package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.rest.data.DataService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Component
@RequiredArgsConstructor
public class BuildDataZipUseCase {

    private final DataService dataService;

    public void execute(Path tempDirectory, DownloadDatasetQuery downloadDatasetQuery) {
        dataService.buildDataZip(tempDirectory, downloadDatasetQuery);
    }
}
