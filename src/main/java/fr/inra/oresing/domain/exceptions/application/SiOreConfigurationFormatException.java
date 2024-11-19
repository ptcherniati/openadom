package fr.inra.oresing.domain.exceptions.application;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Map;

@EqualsAndHashCode(callSuper = true)
@Value
@JsonIgnoreProperties({"stackTrace", "detailMassage", "cause", "depth", "suppressedExeceptions"})
public class
SiOreConfigurationFormatException extends IllegalArgumentException{
    ConfigurationException exception;
    Map<String, Object> params;
}