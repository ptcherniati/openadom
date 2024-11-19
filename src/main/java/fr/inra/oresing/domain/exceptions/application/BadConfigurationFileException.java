package fr.inra.oresing.domain.exceptions.application;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Map;

@Value
@EqualsAndHashCode(callSuper=false)
public class BadConfigurationFileException extends OreSiTechnicalException {
    Map<String, Object> params;

    public BadConfigurationFileException(final String name, final Map<String, Object> params) {
        super(name);
        this.params = params;
    }
}