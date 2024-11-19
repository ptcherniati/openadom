package fr.inra.oresing.domain.exceptions.authentication.authentication;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.Authorization;
import lombok.Getter;

import java.util.List;
@Getter
public class NotApplicationCanSetRightsException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_SET_RIGHTS_APPLICATION = "NO_RIGHT_FOR_SET_RIGHTS_APPLICATION";
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