package fr.inra.oresing.rest;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Value
public class AuthorizationParsed {
    private List<String> dataGroup;
    private Map<String, String> requiredauthorizations;
    java.time.LocalDate fromDay;
    LocalDate toDay;
}