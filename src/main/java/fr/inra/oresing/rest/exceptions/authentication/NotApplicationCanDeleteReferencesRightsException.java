package fr.inra.oresing.rest.exceptions.authentication;

import fr.inra.oresing.OreSiTechnicalException;
import lombok.Getter;

import java.util.List;

@Getter
public class NotApplicationCanDeleteReferencesRightsException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_DELETE_REFERENCES_RIGHTS_APPLICATION = "NO_RIGHT_FOR_DELETE_REFERENCES_RIGHTS_APPLICATION";
    String applicationName;
    List<String> authorizationsRestrictions;
    public NotApplicationCanDeleteReferencesRightsException(String applicationName) {
        super(NO_RIGHT_FOR_DELETE_REFERENCES_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.authorizationsRestrictions = List.of();
    }
    public NotApplicationCanDeleteReferencesRightsException(String applicationName, List<String> authorizationsRestrictions) {
        super(NO_RIGHT_FOR_DELETE_REFERENCES_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.authorizationsRestrictions = authorizationsRestrictions;
    }
}