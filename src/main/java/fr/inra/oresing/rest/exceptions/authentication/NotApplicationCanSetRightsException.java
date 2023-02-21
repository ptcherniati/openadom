package fr.inra.oresing.rest.exceptions.authentication;

import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.model.Authorization;
import lombok.Getter;

import java.util.List;
@Getter
public class NotApplicationCanSetRightsException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_SET_RIGHTS_APPLICATION = "NO_RIGHT_FOR_SET_RIGHTS_APPLICATION";
    String applicationName;
    List<Authorization> authorizationsRestrictions;
    public NotApplicationCanSetRightsException(String applicationName) {
        super(NO_RIGHT_FOR_SET_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.authorizationsRestrictions = List.of();
    }
    public NotApplicationCanSetRightsException(String applicationName, List<Authorization> authorizationsRestrictions) {
        super(NO_RIGHT_FOR_SET_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.authorizationsRestrictions = authorizationsRestrictions;
    }
}