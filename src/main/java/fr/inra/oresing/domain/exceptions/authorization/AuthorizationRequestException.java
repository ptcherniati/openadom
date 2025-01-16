package fr.inra.oresing.domain.exceptions.authorization;

import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

@Getter
public enum AuthorizationRequestException {
    INVALID_REQUEST,
    NO_AUTHORIZATION_NAME,
    NO_USER_IDS,
    UNKNOWN_USER_IDS,
    NO_AUTHORIZATIONS,
    BAD_OPERATION_TYPE,
    BAD_REFERENCES,

    BAD_FILE_NAME_START_DATE,
    BAD_FILE_NAME_END_DATE,
    INVAALID_FILE_NAME,

    MISSING_REQUIRED_AUTHORIZATION,
    NO_RIGHT_ON_TABLE_FOR_DEPOSIT;


    private final String message;

    AuthorizationRequestException() {
        message = toMessage(name());
    }

    private static String toMessage(final String name) {
        final String message = Arrays.stream(name.split("_"))
                .map(n -> n.charAt(0) + n.substring(1).toLowerCase())
                .collect(Collectors.joining());
        return message.replaceFirst("^.", message.substring(0, 1).toLowerCase());
    }
}
