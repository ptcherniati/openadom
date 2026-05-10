package fr.inra.oresing.persistence;

/**
 * @deprecated Déplacé vers {@link fr.inra.oresing.domain.checker.type.SqlPrimitiveType}.
 *             Cet alias sera supprimé dans une prochaine version.
 */
@Deprecated(forRemoval = true)
public enum SqlPrimitiveType {
    UUID,
    LTREE,
    TEXT,
    INTEGER,
    NUMERIC,
    COMPOSITE_DATE,
    BOOLEAN,
    JSONB;

    public String getSql() { return name(); }

    public boolean isEmptyStringValidValue() {
        return java.util.Set.of(TEXT, LTREE).contains(this);
    }
}