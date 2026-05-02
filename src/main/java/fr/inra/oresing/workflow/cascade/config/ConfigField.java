package fr.inra.oresing.workflow.cascade.config;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Definition declarative d'un field de configuration : type , getter ,
 * setter , validator , metadonnees UI ( hot/cold , description ,
 * valeurs autorisees ) .
 *
 * <p>Utilise par {@link ConfigFieldRegistry} pour eviter la duplication
 * dans {@code ConfigEditService} ( un seul source of truth pour la
 * mutation , la validation , l'affichage cote UI et l'audit ) .
 *
 * @param <T> type du field ( Integer , Boolean , String , Enum )
 *
 * @author R.YAHIAOUI
 */
public final class ConfigField<T> {

    public enum Type { INT, BOOL, STRING, ENUM }

    private final String name;
    private final Type   type;
    private final boolean hot;
    private final boolean restartRequired;
    private final String description;
    private final Supplier<T> getter;
    private final Consumer<T> setter;          // null si read-only ( cold )
    private final Validator<T> validator;      // null si pas de validation
    private final List<String> allowedValues;  // non-null pour ENUM
    private final java.util.function.Function<String, T> parser;

    private ConfigField(Builder<T> b) {
        this.name = b.name;
        this.type = b.type;
        this.hot = b.hot;
        this.restartRequired = b.restartRequired;
        this.description = b.description;
        this.getter = b.getter;
        this.setter = b.setter;
        this.validator = b.validator;
        this.allowedValues = b.allowedValues;
        this.parser = b.parser;
    }

    public String name()                    { return name; }
    public Type type()                      { return type; }
    public boolean hot()                    { return hot; }
    public boolean restartRequired()        { return restartRequired; }
    public boolean isReadOnly()             { return setter == null; }
    public String description()             { return description; }
    public List<String> allowedValues()     { return allowedValues; }

    /** Lit la valeur courante via le getter . */
    public T currentValue() { return getter.get(); }

    /**
     * Applique une nouvelle valeur ( parsee + validee ) . Le caller doit
     * deja avoir verifie {@code !isReadOnly()} ; sinon
     * {@link UnsupportedOperationException} .
     */
    public Mutation<T> apply(Object rawValue) {
        if (isReadOnly()) {
            throw new UnsupportedOperationException(
                    "Field " + name + " is read-only ( restart required )");
        }
        T oldValue = getter.get();
        T newValue = parser != null ? parser.apply(String.valueOf(rawValue)) : cast(rawValue);
        if (validator != null) {
            validator.validate(name, newValue);
        }
        if (java.util.Objects.equals(oldValue, newValue)) {
            return new Mutation<>(name, oldValue, newValue, false);
        }
        setter.accept(newValue);
        return new Mutation<>(name, oldValue, newValue, true);
    }

    @SuppressWarnings("unchecked")
    private T cast(Object raw) {
        if (raw == null) return null;
        if (type == Type.INT && raw instanceof Number n) return (T) Integer.valueOf(n.intValue());
        if (type == Type.BOOL && raw instanceof Boolean b) return (T) b;
        if (type == Type.STRING && raw instanceof String s) return (T) s;
        return (T) raw;
    }

    public record Mutation<T>(String field, T oldValue, T newValue, boolean changed) {
        public String oldString() { return Optional.ofNullable(oldValue).map(String::valueOf).orElse(null); }
        public String newString() { return Optional.ofNullable(newValue).map(String::valueOf).orElse(null); }
    }

    @FunctionalInterface
    public interface Validator<T> {
        void validate(String fieldName, T value);
    }

    // ---- Factory builders ----

    public static Builder<Integer> intField(String name) {
        return new Builder<Integer>().name(name).type(Type.INT)
                .parser(Integer::parseInt);
    }

    public static Builder<Boolean> boolField(String name) {
        return new Builder<Boolean>().name(name).type(Type.BOOL)
                .parser(Boolean::parseBoolean);
    }

    public static Builder<String> stringField(String name) {
        return new Builder<String>().name(name).type(Type.STRING)
                .parser(s -> s);
    }

    public static <E extends Enum<E>> Builder<String> enumField(String name, Class<E> enumClass) {
        List<String> allowed = java.util.Arrays.stream(enumClass.getEnumConstants())
                .map(Enum::name).toList();
        return new Builder<String>().name(name).type(Type.ENUM)
                .allowedValues(allowed)
                .parser(s -> {
                    Enum.valueOf(enumClass, s);   // throws IllegalArgumentException si pas valide
                    return s;
                });
    }

    public static final class Builder<T> {
        private String name;
        private Type type;
        private boolean hot = true;
        private boolean restartRequired = false;
        private String description = "";
        private Supplier<T> getter;
        private Consumer<T> setter;
        private Validator<T> validator;
        private List<String> allowedValues;
        private java.util.function.Function<String, T> parser;

        public Builder<T> name(String n)          { this.name = n; return this; }
        public Builder<T> type(Type t)            { this.type = t; return this; }
        public Builder<T> hot()                   { this.hot = true; this.restartRequired = false; return this; }
        public Builder<T> readOnly(String why)    {
            this.hot = false; this.restartRequired = true;
            this.description = (description == null || description.isBlank() ? why : description);
            return this;
        }
        public Builder<T> description(String d)   { this.description = d; return this; }
        public Builder<T> getter(Supplier<T> g)   { this.getter = g; return this; }
        public Builder<T> setter(Consumer<T> s)   { this.setter = s; return this; }
        public Builder<T> validator(Validator<T> v) { this.validator = v; return this; }
        public Builder<T> allowedValues(List<String> v) { this.allowedValues = v; return this; }
        public Builder<T> parser(java.util.function.Function<String, T> p) { this.parser = p; return this; }
        public Builder<T> range(int min, int max) {
            this.validator = (n, v) -> {
                int i = ((Number) v).intValue();
                if (i < min || i > max) {
                    throw new IllegalArgumentException(
                            n + " must be in [" + min + " ; " + max + "] ( got " + i + " )");
                }
            };
            return this;
        }
        public Builder<T> notBlank() {
            @SuppressWarnings("unchecked")
            Validator<T> v = (n, val) -> {
                String s = String.valueOf(val);
                if (s == null || s.isBlank()) {
                    throw new IllegalArgumentException(n + " must not be blank");
                }
            };
            this.validator = v;
            return this;
        }

        public ConfigField<T> build() {
            if (name == null) throw new IllegalStateException("name required");
            if (type == null) throw new IllegalStateException("type required");
            if (getter == null) throw new IllegalStateException("getter required for " + name);
            return new ConfigField<>(this);
        }
    }
}
