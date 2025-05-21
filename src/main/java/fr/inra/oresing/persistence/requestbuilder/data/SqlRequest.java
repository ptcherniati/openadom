package fr.inra.oresing.persistence.requestbuilder.data;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public record SqlRequest(String sql, MapSqlParameterSource parameterSource) {
}