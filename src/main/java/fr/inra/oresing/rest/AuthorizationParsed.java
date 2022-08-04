package fr.inra.oresing.rest;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Value
public class AuthorizationParsed {
    String path;
    List<String> dataGroups;
    Map<String, String> requiredAuthorizations;
    LocalDate fromDay;
    LocalDate toDay;
}