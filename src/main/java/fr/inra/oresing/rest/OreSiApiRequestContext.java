package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiRequestClient;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class OreSiApiRequestContext {

    private static final InheritableThreadLocal<OreSiApiRequestContext> context = new InheritableThreadLocal<>();

    private OreSiRequestClient requestClient;

    private String clientCorrelationId;

    public static void reset() {
        context.set(null);
    }

    public static OreSiApiRequestContext get() {
        OreSiApiRequestContext oreSiContext = context.get();
        if (oreSiContext == null) {
            oreSiContext = new OreSiApiRequestContext();
            context.set(oreSiContext);
        }
        return oreSiContext;
    }
}
