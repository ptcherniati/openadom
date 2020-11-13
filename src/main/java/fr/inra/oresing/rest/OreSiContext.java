package fr.inra.oresing.rest;

import fr.inra.oresing.model.OreSiUser;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class OreSiContext {

    private static final InheritableThreadLocal<OreSiContext> context = new InheritableThreadLocal<>();

    private OreSiUser user;

    private String clientCorrelationId;

    public static void reset() {
        context.set(null);
    }

    public static OreSiContext get() {
        OreSiContext oreSiContext = context.get();
        if (oreSiContext == null) {
            oreSiContext = new OreSiContext();
            context.set(oreSiContext);
        }
        return oreSiContext;
    }

    public void setUser(OreSiUser user) {
        this.user = user;
    }
}
