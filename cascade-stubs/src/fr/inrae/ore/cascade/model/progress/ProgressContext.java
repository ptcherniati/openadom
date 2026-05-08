package fr.inrae.ore.cascade.model.progress;
public class ProgressContext {
    private static final ProgressContext NOOP = new ProgressContext();
    public static ProgressContext current() { return NOOP; }
    public void emit(long delta) {}
}
