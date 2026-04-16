package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.rest.data.DataService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FindDataUseCase {
    private final DataService dataService;

    public FindDataUseCase(DataService dataService) {
        this.dataService = dataService;
    }

    public List<DataRow> execute(DownloadDatasetQuery downloadDatasetQuery) {
        return dataService.findData(downloadDatasetQuery);
    }
}
