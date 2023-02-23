package fr.inra.oresing.rest.exceptions.data;

import fr.inra.oresing.OreSiTechnicalException;

public class BadDownloadDatasetQuery extends OreSiTechnicalException {
    public BadDownloadDatasetQuery(String message) {
        super(message);
    }
}