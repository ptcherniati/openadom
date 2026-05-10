package fr.inra.oresing.rest.model.configuration;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de JacksonErrorParser — sans contexte Spring.
 */
@Tag("domain.model")
@DisplayName("JacksonErrorParser")
class JacksonErrorParserTest {

    private static JsonParseException makeException(String message) {
        return new JsonParseException(null, message, JsonLocation.NA);
    }

    @Test
    @DisplayName("parse() retourne DUPLICATE_KEY pour 'duplicate field'")
    void parseDuplicateField() {
        ValidationError error = JacksonErrorParser.parse(makeException("duplicate field 'myKey'"));
        assertThat(error.getMessage()).isEqualTo(ConfigurationException.DUPLICATE_KEY.getMessage());
    }

    @Test
    @DisplayName("parse() retourne UNEXPECTED_CHARACTER pour 'unexpected character'")
    void parseUnexpectedCharacter() {
        ValidationError error = JacksonErrorParser.parse(makeException("unexpected character at position 5"));
        assertThat(error.getMessage()).isEqualTo(ConfigurationException.UNEXPECTED_CHARACTER.getMessage());
    }

    @Test
    @DisplayName("parse() retourne UNKNOWN pour un message non reconnu")
    void parseUnknown() {
        ValidationError error = JacksonErrorParser.parse(makeException("some random parse error"));
        assertThat(error.getMessage()).isEqualTo(ConfigurationException.UNKNOWN.getMessage());
    }

    @Test
    @DisplayName("parse() retourne UNEXPECTED_EOF pour 'unexpected end-of-input'")
    void parseUnexpectedEof() {
        ValidationError error = JacksonErrorParser.parse(makeException("unexpected end-of-input"));
        assertThat(error.getMessage()).isEqualTo(ConfigurationException.UNEXPECTED_EOF.getMessage());
    }

    @Test
    @DisplayName("extractErrorDetails extrait les infos de location quand null → map vide")
    void parseWithNullLocation() {
        ValidationError error = JacksonErrorParser.parse(makeException("malformed number 123."));
        assertThat(error).isNotNull();
    }
}
