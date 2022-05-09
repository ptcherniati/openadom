package fr.inra.oresing.rest.exceptions;

import lombok.Value;

import java.util.Map;

@Value
public class SiOreIllegalArgumentException extends IllegalArgumentException{
    String message;
    Map<String, Object> params;
}