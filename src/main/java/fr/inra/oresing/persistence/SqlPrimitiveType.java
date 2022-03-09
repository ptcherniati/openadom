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
    COMPOSITE_DATE;

    /**
     * Le type en SQL, tel qu'il faut l'écrire pour faire un cast
     */
    public String getSql() {
        return name();
    }

    /**
     * Est-ce que la chaîne vide peut être convertie dans ce type.
     *
     * Par exemple <code>SELECT ''::UUID</code> donne <code>invalid input syntax for type uuid: ""</code> donc non
     */
    public boolean isEmptyStringValidValue() {
        return Set.of(TEXT, LTREE).contains(this);
    }
}