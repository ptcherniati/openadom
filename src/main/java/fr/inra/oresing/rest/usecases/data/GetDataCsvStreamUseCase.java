package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.rest.data.DataService;
import org.springframework.stereotype.Component;

import java.io.OutputStream;
import java.util.Locale;

@Component
public class GetDataCsvStreamUseCase {
    private final DataService dataService;

    public GetDataCsvStreamUseCase(DataService dataService) {
        this.dataService = dataService;
    }

    public void execute(OutputStream outputStream, String applicationNameOrId, String dataName, Locale language, boolean horizontalDisplay) {
        dataService.getDataCsvStream(outputStream, applicationNameOrId, dataName, language, horizontalDisplay);
    }
}
