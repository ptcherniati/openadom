package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import lombok.Value;
import org.assertj.core.util.Strings;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value
public class SqlSchemaForApplication implements SqlSchema {

    Application application;

    @Override
    public String getName() {
        return application.getName();
    }

    public SqlTable data() {
        return new SqlTable(this, "data");
    }

    public SqlTable referenceValue() {
        return new SqlTable(this, "referenceValue");
    }

    public SqlTable binaryFile() {
        return new SqlTable(this, "binaryFile");
    }

    public SqlTable synthesis() {  return new SqlTable(this, "OreSiSynthesis");  }

    public SqlTable authorization() {
        return new SqlTable(this, "oreSiAuthorization");
    }

    public SqlTable forTableName(String tableName) {
        return new SqlTable(this, tableName);
    }

    public String requiredAuthorizationsAttributes(Application app) {
        return app.getConfiguration().getRequiredAuthorizationsAttributes().stream()
                .map(s -> String.format("%s ltree", s))
                .collect(Collectors.joining(",\n"));
    }

    private Stream<String> getAttributes() {
        return Optional.ofNullable(application)
                .map(a -> application.getConfiguration())
                .map(c -> c.getDataTypes())
                .map(d -> {
                    return d.entrySet()
                            .stream()
                            .map(entry -> {
                                return Optional.ofNullable(entry)
                                        .map(e -> e.getValue().getAuthorization())
                                        .map(a -> a.getAuthorizationScopes())
                                        .map(as -> as.keySet().stream())
                                        .map(s -> s.collect(Collectors.toSet()))
                                                .orElse(null);
                            })
                            .filter(c->c!=null)
                            .flatMap(Set::stream)
                            .distinct();
                })
                .orElse(Set.of("").stream());
    }

    public String requiredAuthorizationsAttributesComparing(Application app) {
        String requiredAuthorizationsAttributesComparing = app.getConfiguration().getRequiredAuthorizationsAttributes().stream()
                .map(attribute -> String.format(
                        "((authorized).requiredAuthorizations.%1$s is null or (COALESCE((authorized).requiredAuthorizations.%1$s, ''::ltree) @> COALESCE((\"authorization\").requiredAuthorizations.%1$s, ''::ltree)))",
                        attribute
                ))
                .collect(Collectors.joining("\n AND "));
        return requiredAuthorizationsAttributesComparing + (Strings.isNullOrEmpty(requiredAuthorizationsAttributesComparing)?"":" AND\n ");
    }
}