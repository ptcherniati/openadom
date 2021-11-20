package fr.inra.oresing.rest;

import lombok.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class GetAuthorizationResult {
    UUID id;
    List<UUID> users;
    UUID application;
    String dataType;
    String dataGroup;
    Map<String, String> authorizedScopes;
    LocalDate fromDay;
    LocalDate toDay;
}