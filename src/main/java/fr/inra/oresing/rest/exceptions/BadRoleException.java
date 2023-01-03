package fr.inra.oresing.rest.exceptions;

import fr.inra.oresing.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class BadRoleException  extends OreSiTechnicalException {
    String role;

    public BadRoleException(String message, String role, Throwable cause) {
        super(message, cause);
        this.role = role;
    }

    public BadRoleException(String message, String role) {
        super(message);
        this.role = role;
    }
}