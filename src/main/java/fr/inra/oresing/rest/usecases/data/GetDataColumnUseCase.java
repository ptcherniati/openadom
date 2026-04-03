package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.data.DataService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GetDataColumnUseCase {
    private final DataService dataService;

    public GetDataColumnUseCase(DataService dataService) {
        this.dataService = dataService;
    }

    public List<List<String>> execute(Application application, String refType, String column) {
        return dataService.getDataColumn(application, refType, column);
    }
}
