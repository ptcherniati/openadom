package fr.inra.oresing.rest.model.configuration;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonLocation;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.HashMap;
import java.util.Map;

public class JacksonErrorParser {

    public static ValidationError parse(JsonParseException e) {
        ConfigurationException errorType = determineErrorType(e);
        Map<String, Object> errorDetails = extractErrorDetails(e);

        return new ValidationError(errorType, errorDetails);
    }

    private static ConfigurationException determineErrorType(JsonParseException e) {
        // Logique pour déterminer le type d'erreur en fonction du message de l'exception
        String errorMessage = e.getOriginalMessage().toLowerCase();

        if (errorMessage.contains("duplicate field")) {
            return ConfigurationException.DUPLICATE_KEY;
        } else if (errorMessage.contains("unexpected character")) {
            return ConfigurationException.UNEXPECTED_CHARACTER;
        } else if (errorMessage.contains("invalid token")) {
            return ConfigurationException.INVALID_TOKEN;
        } else if (errorMessage.contains("unexpected end-of-input")) {
            return ConfigurationException.UNEXPECTED_EOF;
        } else if (errorMessage.contains("malformed number")) {
            return ConfigurationException.MALFORMED_NUMBER;
        } else if (errorMessage.contains("unclosed string")) {
            return ConfigurationException.UNTERMINATED_STRING;
        } else if (errorMessage.contains("malformed comment")) {
            return ConfigurationException.MALFORMED_COMMENT;
        } else {
            return ConfigurationException.UNKNOWN;
        }
    }

    private static Map<String, Object> extractErrorDetails(JsonParseException e) {
        Map<String, Object> details = new HashMap<>();
        JsonLocation location = e.getLocation();

        if (location != null) {
            details.put("lineNumber", location.getLineNr());
            details.put("columnNumber", location.getColumnNr());
        }

        // Ajoutez des informations supplémentaires selon le type d'erreur
        String errorMessage = e.getOriginalMessage().toLowerCase();

        if (errorMessage.contains("duplicate field")) {
            // Logique pour extraire les clés dupliquées si possible
            details.put("duplicateKeys", extractDuplicateKeys(errorMessage));
        }

        return details;
    }

    private static String extractDuplicateKeys(String errorMessage) {
        // Logique pour extraire les clés dupliquées du message d'erreur
        // Exemple de message : "duplicate field 'vcat_label_fr'"
        return errorMessage.substring(errorMessage.indexOf("'") + 1, errorMessage.lastIndexOf("'"));
    }
}