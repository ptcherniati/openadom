package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.OreSiEntity;
import fr.inra.oresing.domain.application.Application;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public abstract class JsonTableInApplicationSchemaRepositoryTemplate<T extends OreSiEntity> extends JsonTableRepositoryTemplate<T> {

    private final Application application;

    private final SqlSchemaForApplication schema;

    public JsonTableInApplicationSchemaRepositoryTemplate(final Application application) {
        super();
        this.application = application;
        schema = SqlSchema.forApplication(application);
    }

    public static String escapeSql(final String string) {
        return Optional.ofNullable(string)
                .map(s -> s.replaceAll("'", "''"))
                .orElse(null);
    }

    protected SqlSchemaForApplication getSchema() {
        return schema;
    }

    protected Application getApplication() {
        return application;
    }
    static Map<String, ?> convertMapsqlparameterSourcetoMap(final MapSqlParameterSource sqlParameterSource){
        return Arrays.stream(Objects.requireNonNull(sqlParameterSource.getParameterNames()))
                .collect(Collectors.toMap(param->param, sqlParameterSource::getValue));
    }
}