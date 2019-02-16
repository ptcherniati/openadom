package fr.inra.oresing.rest;

import fr.inra.oresing.model.OreSiUser;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@ToString
@AllArgsConstructor
public class OreSiContext {

    private static final InheritableThreadLocal<OreSiContext> context = new InheritableThreadLocal<>();

    @Getter
    private String role;
    @Getter
    private String clientCorrelationId;

    public static void reset() {
        context.set(null);
    }

    public static OreSiContext get() {
        return context.get();
    }

    public static void set(OreSiContext c) {
        context.set(c);
    }

    public static void setUser(OreSiUser user) {
        set(new OreSiContext(user.getName(), get().getClientCorrelationId()));
    }

}
