package fr.inra.oresing.rest;

public enum ViewStrategy {

    /**
     * Les vues relationnalles sont désactivées
     */
    DISABLED,

    /**
     * Les vues relationnelles sont créées sous forme de vues (<code>CREATE VIEW ...</code>).
     */
    VIEW,

    /**
     * Les vues relationnelles sont créées sous forme de tables préremplies (<code>CREATE TABLE ... AS SELECT ...</code>).
     */
    TABLE;

    public boolean isEnabled() {
        return this != DISABLED;
    }

    public boolean isRecreationOnDataUpdateRequired() {
        return this == TABLE;
    }
}
