package fr.inra.oresing.rest.exceptions.authentication;

import fr.inra.oresing.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class NotApplicationCanSetRightsReferencesException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_SET_RIGHTS_REFERENCES_APPLICATION = "NO_RIGHT_FOR_SET_RIGHTS_REFERENCES_APPLICATION";
    String applicationName;
    public NotApplicationCanSetRightsReferencesException(String applicationName) {
        super(NO_RIGHT_FOR_SET_RIGHTS_REFERENCES_APPLICATION);
        this.applicationName = applicationName;
    }
}