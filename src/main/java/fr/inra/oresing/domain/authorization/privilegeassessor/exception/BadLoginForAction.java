package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class BadLoginForAction extends OreSiTechnicalException {
    public static final String BAD_LOGIN_FOR_ACTION = "BAD_LOGIN_FOR_ACTION";
    final String login;

    public BadLoginForAction(final String login) {
        super(BAD_LOGIN_FOR_ACTION);
        this.login = login;
    }
}