package fr.inra.oresing.rest.rightsrequest;

import fr.inra.oresing.OreSiTechnicalException;

public class BadRightsRequestOrUUIDQuery extends OreSiTechnicalException {
    public BadRightsRequestOrUUIDQuery(String message) {
        super(message);
    }
}