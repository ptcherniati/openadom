package fr.inra.oresing.rest;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
public class OreSiContext {

    private static final InheritableThreadLocal<OreSiContext> context = new InheritableThreadLocal<>();

    @Getter
    private String role;
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

}
