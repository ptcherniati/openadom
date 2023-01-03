package fr.inra.oresing.rest.exceptions;

import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.model.Authorization;
import lombok.Getter;

import java.util.List;

@Getter
public class NotApplicationCanDeleteRightsException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION = "NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION";
    String applicationName;
    String dataType;
    List<Authorization> authorizationsRestrictions;
    public NotApplicationCanDeleteRightsException(String applicationName, String dataType) {
        super(NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.dataType = dataType;
        this.authorizationsRestrictions = List.of();
    }
    public NotApplicationCanDeleteRightsException(String applicationName, String dataType, List<Authorization> authorizationsRestrictions) {
        super(NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.dataType = dataType;
        this.authorizationsRestrictions = authorizationsRestrictions;
    }
}