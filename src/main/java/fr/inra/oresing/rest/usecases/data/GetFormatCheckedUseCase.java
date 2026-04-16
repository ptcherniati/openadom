package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.rest.data.DataService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GetFormatCheckedUseCase {
    private final DataService dataService;

    public GetFormatCheckedUseCase(DataService dataService) {
        this.dataService = dataService;
    }

    public Map<String, Map<String, LineChecker>> execute(String nameOrId, String references) {
        return dataService.getFormatChecked(nameOrId, references);
    }
}
