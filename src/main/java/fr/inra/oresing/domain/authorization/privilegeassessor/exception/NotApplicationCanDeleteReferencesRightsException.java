package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

import java.util.List;

@Getter
public class NotApplicationCanDeleteReferencesRightsException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_DELETE_REFERENCES_RIGHTS_APPLICATION = "NO_RIGHT_FOR_DELETE_REFERENCES_RIGHTS_APPLICATION";
    final String applicationName;
    final List<String> authorizationsRestrictions;
    public NotApplicationCanDeleteReferencesRightsException(final String applicationName) {
        super(NO_RIGHT_FOR_DELETE_REFERENCES_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        authorizationsRestrictions = List.of();
    }
    public NotApplicationCanDeleteReferencesRightsException(final String applicationName, final List<String> authorizationsRestrictions) {
        super(NO_RIGHT_FOR_DELETE_REFERENCES_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.authorizationsRestrictions = authorizationsRestrictions;
    }
}