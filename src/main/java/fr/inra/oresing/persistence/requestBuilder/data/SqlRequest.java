package fr.inra.oresing.persistence.requestBuilder.data;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

public record SqlRequest(String sql, MapSqlParameterSource parameterSource) {
}
