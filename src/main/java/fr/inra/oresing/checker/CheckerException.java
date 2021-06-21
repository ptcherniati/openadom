package fr.inra.oresing.checker;

import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.rest.CsvRowValidationCheckResult;

import java.util.List;

public class CheckerException extends OreSiTechnicalException {

    private final List<CsvRowValidationCheckResult> errors;

    public CheckerException(List<CsvRowValidationCheckResult> errors) {
        super("Erreurs rencontrées à l'import du fichier");
        this.errors = errors;
    }

    public List<CsvRowValidationCheckResult> getErrors() {
        return errors;
    }
}
