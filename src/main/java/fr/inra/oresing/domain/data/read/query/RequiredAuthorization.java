package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.configuration.Ltree;

public record RequiredAuthorization(String compositereferenceLabel, Ltree path) {
}
