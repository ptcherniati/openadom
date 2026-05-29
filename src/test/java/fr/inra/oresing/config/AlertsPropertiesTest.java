package fr.inra.oresing.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link AlertsProperties} - parsing CSV des
 * destinataires d'alertes . Aucun contexte Spring requis .
 */
@Tag("core.config")
@DisplayName("AlertsProperties - parsing CSV recipients")
class AlertsPropertiesTest {

    @Test
    @DisplayName("recipients vide par defaut -> liste vide")
    void defaultEmpty() {
        AlertsProperties props = new AlertsProperties();
        assertTrue(props.getRecipientsList().isEmpty());
    }

    @Test
    @DisplayName("recipients null setter -> stocke chaine vide")
    void nullSetterNeverNull() {
        AlertsProperties props = new AlertsProperties();
        props.setRecipients(null);
        assertEquals("", props.getRecipients());
        assertTrue(props.getRecipientsList().isEmpty());
    }

    @Test
    @DisplayName("recipients unique mail -> liste 1 element")
    void singleRecipient() {
        AlertsProperties props = new AlertsProperties();
        props.setRecipients("admin@inrae.fr");
        assertEquals(List.of("admin@inrae.fr"), props.getRecipientsList());
    }

    @Test
    @DisplayName("recipients comma-separated -> split + trim")
    void multipleRecipientsTrimmed() {
        AlertsProperties props = new AlertsProperties();
        props.setRecipients("  admin@inrae.fr , ops@inrae.fr,oncall@inrae.fr  ");
        assertEquals(
                List.of("admin@inrae.fr", "ops@inrae.fr", "oncall@inrae.fr"),
                props.getRecipientsList()
        );
    }

    @Test
    @DisplayName("recipients avec entrees vides -> filtrees")
    void filterEmptyEntries() {
        AlertsProperties props = new AlertsProperties();
        props.setRecipients("admin@inrae.fr,,  ,ops@inrae.fr");
        assertEquals(
                List.of("admin@inrae.fr", "ops@inrae.fr"),
                props.getRecipientsList()
        );
    }

    @Test
    @DisplayName("getRecipientsList retourne liste immutable")
    void immutableList() {
        AlertsProperties props = new AlertsProperties();
        props.setRecipients("admin@inrae.fr");
        List<String> list = props.getRecipientsList();
        assertThrows(UnsupportedOperationException.class, () -> list.add("evil@bad.com"));
    }
}
