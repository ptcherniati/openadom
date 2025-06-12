package fr.inra.oresing.domain.exceptions.role.role;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class BadRoleException extends OreSiTechnicalException {
    final String role;

    public BadRoleException(final String message, final String role, final Throwable cause) {
        super(message, cause);
        this.role = role;
    }

    public BadRoleException(final String message, final String role) {
        super(message);
        this.role = role;
    }
}