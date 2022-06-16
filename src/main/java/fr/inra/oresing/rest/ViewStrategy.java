package fr.inra.oresing.rest;

import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

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

    public static SiOreIllegalArgumentException getError(ViewStrategy viewStrategy) {
        return new SiOreIllegalArgumentException(
                "badViewStrategie",
                Map.of(
                        "viewStrategy", viewStrategy,
                        "knownViewStrategies", Arrays.stream(ViewStrategy.values()).map(ViewStrategy::toString).collect(Collectors.toSet())
                )
        );
    }

    public boolean isEnabled() {
        return this != DISABLED;
    }

    public boolean isRecreationOnDataUpdateRequired() {
        return this == TABLE;
    }
}