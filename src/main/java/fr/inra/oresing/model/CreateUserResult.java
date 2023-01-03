package fr.inra.oresing.model;

import lombok.Value;

import java.util.UUID;

@Value
public class CreateUserResult {
    UUID userId;
}