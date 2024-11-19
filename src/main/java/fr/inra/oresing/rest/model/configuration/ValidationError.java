package fr.inra.oresing.rest.model.configuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;

@Getter
public class ValidationError extends Throwable {
    Map<String, Object> params;

    public ValidationError(final ConfigurationException message, final Map<String, Object> params) {
        super(message.getMessage());
        this.params = params;
    }

    public ValidationError(final String message) {
        super(message);
    }

    public Object getParam(final String param) {
        return Optional.ofNullable(getParams())
                .map(p -> p.get(param))
                .orElse("");
    }

    public String getValidationErrorString() {
        return new ToStringValidation(getMessage(), params).toString();
    }

    public Record toJsonObject() {
        record JsonString(String message, Map<String, Object> params){}
        return new JsonString(getMessage(), getParams());
    }

    record ToStringValidation(String error, Map<String, Object> params) {

        @Override
        public String toString() {
            try {
                return new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(
                        new ToStringValidation(error(), params())
                );
            } catch (final JsonProcessingException e) {
                return "null";
            }
        }
    }
}
