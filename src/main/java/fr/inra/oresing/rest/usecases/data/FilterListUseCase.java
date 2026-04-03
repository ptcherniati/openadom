package fr.inra.oresing.rest.usecases.data;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.FilterList;
import fr.inra.oresing.rest.data.DataService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class FilterListUseCase {
    private final DataService dataService;

    public FilterListUseCase(DataService dataService) {
        this.dataService = dataService;
    }

    public Flux<FilterList> execute(Application application, String refType) {
        return dataService.filterList(application, refType);
    }
}
