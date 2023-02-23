package fr.inra.oresing.rest.exceptions.authentication;

import fr.inra.oresing.OreSiTechnicalException;
import lombok.Getter;

import java.util.List;

@Getter
public class NotApplicationCanManageReferenceRightsException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_MANAGE_REFERENCES_RIGHTS_APPLICATION = "NO_RIGHT_FOR_MANAGE_REFERENCES_RIGHTS_APPLICATION";
    String applicationName;
    String dataType;
    List<String> authorizationsRestrictions;
    public NotApplicationCanManageReferenceRightsException(String applicationName) {
        super(NO_RIGHT_FOR_MANAGE_REFERENCES_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.authorizationsRestrictions = List.of();
    }
    public NotApplicationCanManageReferenceRightsException(String applicationName, List<String> authorizationsRestrictions) {
        super(NO_RIGHT_FOR_MANAGE_REFERENCES_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.authorizationsRestrictions = authorizationsRestrictions;
    }
}