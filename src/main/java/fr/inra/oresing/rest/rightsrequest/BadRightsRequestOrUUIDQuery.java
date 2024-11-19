package fr.inra.oresing.rest.rightsrequest;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class BadRightsRequestOrUUIDQuery extends OreSiTechnicalException {
    public BadRightsRequestOrUUIDQuery(final String message) {
        super(message);
    }
}