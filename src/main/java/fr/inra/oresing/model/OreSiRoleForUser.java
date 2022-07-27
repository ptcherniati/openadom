package fr.inra.oresing.model;

import lombok.Value;

@Value
public class OreSiRoleForUser {
    private String userId;
    private String role;
    private String applicationPattern;
}