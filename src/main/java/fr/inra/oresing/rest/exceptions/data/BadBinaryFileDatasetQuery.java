package fr.inra.oresing.rest.exceptions.data;

import fr.inra.oresing.OreSiTechnicalException;

public class BadBinaryFileDatasetQuery extends OreSiTechnicalException {
    public BadBinaryFileDatasetQuery(String message) {
        super(message);
    }
}