package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

import java.util.List;

@Getter
public class IllegalRoleToBeGranted extends OreSiTechnicalException {
    public final static String ILLEGAL_ROLE_TO_BE_GRANTED = "ILLEGAL_ROLE_TO_BE_GRANTED";
    final String role;
    public IllegalRoleToBeGranted(final String role) {
        super(ILLEGAL_ROLE_TO_BE_GRANTED);
        this.role = role;
    }
}