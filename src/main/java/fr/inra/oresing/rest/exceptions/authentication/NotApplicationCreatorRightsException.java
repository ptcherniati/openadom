package fr.inra.oresing.rest.exceptions.authentication;

import fr.inra.oresing.OreSiTechnicalException;

import java.util.List;

public class NotApplicationCreatorRightsException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_APPLICATION_CREATION = "NO_RIGHT_FOR_APPLICATION_CREATION";
    public String applicationName;
    public List<String> applicationRestrictions;
    public NotApplicationCreatorRightsException(String applicationName) {
        super(NO_RIGHT_FOR_APPLICATION_CREATION);
        this.applicationName = applicationName;
        this.applicationRestrictions = List.of();
    }
    public NotApplicationCreatorRightsException(String applicationName, List<String> applicationRestrictions) {
        super(NO_RIGHT_FOR_APPLICATION_CREATION);
        this.applicationName = applicationName;
        this.applicationRestrictions = applicationRestrictions;
    }

    public NotApplicationCreatorRightsException(Throwable cause) {
        super(NO_RIGHT_FOR_APPLICATION_CREATION, cause);
        this.applicationRestrictions = List.of();
    }
}