package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class IllegalUserToBeGranted extends OreSiTechnicalException {
    public final static String ILLEGAL_ROLE_TO_BE_GRANTED = "ILLEGAL_ROLE_TO_BE_GRANTED";
    final String login;
    final String applicationName;
    public IllegalUserToBeGranted(final OreSiUser user, String applicationName) {
        super(ILLEGAL_ROLE_TO_BE_GRANTED);
        this.login = user.getLogin();
        this.applicationName = applicationName;
    }
}