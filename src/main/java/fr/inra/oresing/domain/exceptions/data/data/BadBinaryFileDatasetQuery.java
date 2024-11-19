package fr.inra.oresing.domain.exceptions.data.data;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class BadBinaryFileDatasetQuery extends OreSiTechnicalException {
    public BadBinaryFileDatasetQuery(final String message) {
        super(message);
    }
}