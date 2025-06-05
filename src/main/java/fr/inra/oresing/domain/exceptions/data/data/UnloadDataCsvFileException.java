package fr.inra.oresing.domain.exceptions.data.data;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.extern.java.Log;

@Log
public class UnloadDataCsvFileException extends OreSiTechnicalException {
    static final String UNLOAD_DATA_CSV_FILE_EXCEPTION = "unloadDataCsvFileException";
    final Exception exception;

    public UnloadDataCsvFileException(final Exception exception) {
        super(UNLOAD_DATA_CSV_FILE_EXCEPTION);
        this.exception = exception;
        log.severe(exception.getMessage());
    }
}