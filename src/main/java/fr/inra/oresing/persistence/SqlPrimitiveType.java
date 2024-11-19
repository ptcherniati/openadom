package fr.inra.oresing.persistence;

import java.util.Set;

/**
 * L'ensemble des types SQL qui peuvent être utilisés pour représenter des valeurs des données.
 */
public enum SqlPrimitiveType {
    UUID,
    LTREE,
    TEXT,
    INTEGER,
    NUMERIC,
    COMPOSITE_DATE,
    BOOLEAN,
    JSONB;

    /**
     * Le type en SQL, tel qu'il faut l'écrire pour faire un cast
     * @return
     */
    public String getSql() {
        return name();
    }

    /**
     * Est-ce que la chaîne vide peut être convertie dans ce type.
     * <p>
     * Par example <code>SELECT ''::UUID</code> donne <code>invalid input syntax for type uuid: ""</code> donc non
     * @return
     */
    public boolean isEmptyStringValidValue() {
        return Set.of(TEXT, LTREE).contains(this);
    }
}