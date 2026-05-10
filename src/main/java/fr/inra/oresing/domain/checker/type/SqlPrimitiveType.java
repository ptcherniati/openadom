package fr.inra.oresing.domain.checker.type;

import java.util.Set;

/**
 * L'ensemble des types SQL primitifs qui peuvent être utilisés pour représenter
 * des valeurs des données.
 *
 * <p>Ce type est un concept métier pur (comment une valeur de donnée est stockée/comparée
 * en base), placé dans le domaine conformément à l'architecture hexagonale.
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
     * Le type en SQL, tel qu'il faut l'écrire pour faire un cast.
     */
    public String getSql() {
        return name();
    }

    /**
     * Est-ce que la chaîne vide peut être convertie dans ce type.
     *
     * <p>Par exemple {@code SELECT ''::UUID} donne
     * {@code invalid input syntax for type uuid: ""} donc non.
     */
    public boolean isEmptyStringValidValue() {
        return Set.of(TEXT, LTREE).contains(this);
    }
}