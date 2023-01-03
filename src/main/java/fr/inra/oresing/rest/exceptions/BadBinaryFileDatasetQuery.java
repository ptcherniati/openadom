package fr.inra.oresing.rest.exceptions;

import fr.inra.oresing.OreSiTechnicalException;

public class BadBinaryFileDatasetQuery extends OreSiTechnicalException {
    public BadBinaryFileDatasetQuery(String message) {
        super(message);
    }
}