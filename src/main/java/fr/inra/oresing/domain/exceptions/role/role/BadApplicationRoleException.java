package fr.inra.oresing.domain.exceptions.role.role;

import fr.inra.oresing.domain.application.Application;

public class BadApplicationRoleException extends RuntimeException {
    final String role;
    final Application application;

    public BadApplicationRoleException(final String message, final String role, final Throwable cause, Application application) {
        super(message, cause);
        this.role = role;
        this.application = application;
    }

    public BadApplicationRoleException(final String message, final String role, Application application) {
        super(message);
        this.role = role;
        this.application = application;
    }
}
