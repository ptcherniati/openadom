package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
@Tag("domain.model")
class ChartDescriptionTest {

    @Test
    void testToSQL() {
        ChartDescription chartDescription = new ChartDescription();
        Assertions.assertEquals("", chartDescription.toSQL());
    }
}