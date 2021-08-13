package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiTechnicalException;

public class BadDownloadDatasetQuery extends OreSiTechnicalException {
    public BadDownloadDatasetQuery(String message) {
        super(message);
    }
}
