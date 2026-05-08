package fr.inrae.ore.cascade.api.defaults.db.staging;
@FunctionalInterface
public interface FinalizeHook {
    void finalize(FinalizeContext ctx, DeferredFinalizeRegistry registry) throws Exception;
}
