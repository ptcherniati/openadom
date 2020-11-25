package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiRequestClient;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Getter
@Setter
@ToString
@Component
@RequestScope
public class OreSiApiRequestContext {

    private OreSiRequestClient requestClient;

    private String clientCorrelationId;

    public void reset() {
        setRequestClient(null);
        setClientCorrelationId(null);
    }
}
