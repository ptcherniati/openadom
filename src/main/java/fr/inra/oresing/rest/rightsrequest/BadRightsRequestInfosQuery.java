package fr.inra.oresing.rest.rightsrequest;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class BadRightsRequestInfosQuery extends OreSiTechnicalException {
    public BadRightsRequestInfosQuery(final String message) {
        super(message);
    }
}