package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.model.data.LineCheckerResult;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GetCheckedFormatComponentsUseCase {
    private final DataService dataService;

    public GetCheckedFormatComponentsUseCase(DataService dataService) {
        this.dataService = dataService;
    }

    public Map<String, Map<String, LineCheckerResult>> execute(String nameOrId, String dataName) {
        return dataService.getCheckedFormatComponents(nameOrId, dataName);
    }
}
