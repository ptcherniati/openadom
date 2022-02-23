package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiTechnicalException;

public class BadBinaryFileDatasetQuery extends OreSiTechnicalException {
    public BadBinaryFileDatasetQuery(String message) {
        super(message);
    }
}
