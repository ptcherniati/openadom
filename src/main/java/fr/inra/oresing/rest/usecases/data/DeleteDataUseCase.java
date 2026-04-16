package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.rest.data.DataService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class DeleteDataUseCase {
    private final DataService dataService;

    public DeleteDataUseCase(DataService dataService) {
        this.dataService = dataService;
    }

    @Transactional
    public List<UUID> execute(DownloadDatasetQuery downloadDatasetQuery) {
        return dataService.deleteData(downloadDatasetQuery);
    }
}
