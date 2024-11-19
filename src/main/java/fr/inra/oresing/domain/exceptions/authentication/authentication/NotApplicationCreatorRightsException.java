package fr.inra.oresing.domain.exceptions.authentication.authentication;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

import java.util.List;
import java.util.Set;

@Getter
public class NotApplicationCreatorRightsException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_APPLICATION_CREATION = "NO_RIGHT_FOR_APPLICATION_CREATION";
    public String applicationName;
    public final Set<String> applicationRestrictions;
    public NotApplicationCreatorRightsException(final String applicationName) {
        super(NO_RIGHT_FOR_APPLICATION_CREATION);
        this.applicationName = applicationName;
        applicationRestrictions = Set.of();
    }
    public NotApplicationCreatorRightsException(final String applicationName, final Set<String> applicationRestrictions) {
        super(NO_RIGHT_FOR_APPLICATION_CREATION);
        this.applicationName = applicationName;
        this.applicationRestrictions = applicationRestrictions;
    }

    public NotApplicationCreatorRightsException(final Throwable cause) {
        super(NO_RIGHT_FOR_APPLICATION_CREATION, cause);
        applicationRestrictions = Set.of();
    }
}