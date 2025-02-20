package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class IllegalRoleToBeGranted extends OreSiTechnicalException {
    public static final String ILLEGAL_ROLE_TO_BE_GRANTED = "ILLEGAL_ROLE_TO_BE_GRANTED";
    final String role;
    public IllegalRoleToBeGranted(final String role) {
        super(ILLEGAL_ROLE_TO_BE_GRANTED);
        this.role = role;
    }
}