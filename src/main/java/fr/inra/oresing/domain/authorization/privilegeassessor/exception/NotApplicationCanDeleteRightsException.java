package fr.inra.oresing.domain.authorization.privilegeassessor.exception;

import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

import java.util.List;

@Getter
public class NotApplicationCanDeleteRightsException extends OreSiTechnicalException {
    public static final String NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION = "NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION";
    final String applicationName;
    final String dataType;
    final List<Authorization> authorizationsRestrictions;
    public NotApplicationCanDeleteRightsException(final String applicationName, final String dataType) {
        super(NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.dataType = dataType;
        authorizationsRestrictions = List.of();
    }
    public NotApplicationCanDeleteRightsException(final String applicationName, final String dataType, final List<Authorization> authorizationsRestrictions) {
        super(NO_RIGHT_FOR_DELETE_RIGHTS_APPLICATION);
        this.applicationName = applicationName;
        this.dataType = dataType;
        this.authorizationsRestrictions = authorizationsRestrictions;
    }
}