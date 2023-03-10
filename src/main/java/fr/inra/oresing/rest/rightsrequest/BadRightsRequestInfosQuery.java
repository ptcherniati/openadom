package fr.inra.oresing.rest.rightsrequest;

import fr.inra.oresing.OreSiTechnicalException;

public class BadRightsRequestInfosQuery extends OreSiTechnicalException {
    public BadRightsRequestInfosQuery(String message) {
        super(message);
    }
}