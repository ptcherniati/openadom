package fr.inra.oresing.domain.exceptions.authentication.authentication;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

@Getter
public class NotApplicationCanSetRightsReferencesException extends OreSiTechnicalException {
    public final static String NO_RIGHT_FOR_SET_RIGHTS_REFERENCES_APPLICATION = "NO_RIGHT_FOR_SET_RIGHTS_REFERENCES_APPLICATION";
    final String applicationName;
    public NotApplicationCanSetRightsReferencesException(final String applicationName) {
        super(NO_RIGHT_FOR_SET_RIGHTS_REFERENCES_APPLICATION);
        this.applicationName = applicationName;
    }
}