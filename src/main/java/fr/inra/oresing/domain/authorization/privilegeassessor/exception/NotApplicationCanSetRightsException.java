package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

import java.util.List;

@Getter
public class NotApplicationCanSetRightsException extends OreSiTechnicalException {
    public static final String NO_RIGHT_FOR_SET_RIGHTS_APPLICATION = "NO_RIGHT_FOR_SET_RIGHTS_APPLICATION";
    final String applicationName;
    final List<Authorization> authorizationsRestrictions;

    public NotApplicationCanSetRightsException(final String applicationName) {
        super(NO_RIGHT_FOR_SET_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        authorizationsRestrictions = List.of();
    }

    public NotApplicationCanSetRightsException(final String applicationName, final List<Authorization> authorizationsRestrictions) {
        super(NO_RIGHT_FOR_SET_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.authorizationsRestrictions = authorizationsRestrictions;
    }
}