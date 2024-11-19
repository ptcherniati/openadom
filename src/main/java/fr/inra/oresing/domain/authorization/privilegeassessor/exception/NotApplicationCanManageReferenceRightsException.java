package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

import java.util.List;

@Getter
public class NotApplicationCanManageReferenceRightsException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_MANAGE_REFERENCES_RIGHTS_APPLICATION = "NO_RIGHT_FOR_MANAGE_REFERENCES_RIGHTS_APPLICATION";
    final String applicationName;
    String dataType;
    final List<String> authorizationsRestrictions;
    public NotApplicationCanManageReferenceRightsException(final String applicationName) {
        super(NO_RIGHT_FOR_MANAGE_REFERENCES_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        authorizationsRestrictions = List.of();
    }
    public NotApplicationCanManageReferenceRightsException(final String applicationName, final List<String> authorizationsRestrictions) {
        super(NO_RIGHT_FOR_MANAGE_REFERENCES_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.authorizationsRestrictions = authorizationsRestrictions;
    }
}