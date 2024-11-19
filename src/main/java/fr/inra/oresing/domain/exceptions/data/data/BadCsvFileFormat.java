package fr.inra.oresing.domain.exceptions.data.data;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;

public class BadCsvFileFormat extends OreSiTechnicalException {
    static final String BAD_CSV_FILE_FORMAT = "badCsvFileFormat";

    public BadCsvFileFormat() {
        super(BAD_CSV_FILE_FORMAT);
    }
}