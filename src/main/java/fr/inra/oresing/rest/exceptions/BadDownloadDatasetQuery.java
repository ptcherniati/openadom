package fr.inra.oresing.rest.exceptions;

import fr.inra.oresing.OreSiTechnicalException;

public class BadDownloadDatasetQuery extends OreSiTechnicalException {
    public BadDownloadDatasetQuery(String message) {
        super(message);
    }
}