package fr.inra.oresing.rest.model.authorization.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;

import java.util.Map;
import java.util.Optional;

public class AuthorizationRequestError extends Throwable {
    Map<String, Object> params;

    public AuthorizationRequestError(final AuthorizationRequestException message, final Map<String, Object> params) {
        super(message.getMessage());
        this.params = params;
    }

    public AuthorizationRequestError(final String message) {
        super(message);
    }

    public Object getParam(final String param) {
        return Optional.ofNullable(params)
                .map(p -> p.get(param))
                .orElse("");
    }

    public String getAuthorizationRequestString() {
        return new ToStringValidation(getMessage(), params).toString();
    }

    public Record toJsonObject() {
        record JsonString(String message, Map<String, Object> params){}
        return new JsonString(getMessage(), params);
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
