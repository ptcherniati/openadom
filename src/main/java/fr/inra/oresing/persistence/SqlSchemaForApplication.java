package fr.inra.oresing.persistence;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Submission;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public record SqlSchemaForApplication(Application application) implements SqlSchema {
    public static final UUID PUBLIC_UUID = UUID.fromString("9032ffe5-bfc1-453d-814e-287cd678484a");

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

    public SqlTable synthesis() {
        return new SqlTable(this, "OreSiSynthesis");
    }

    public SqlTable additionalBinaryFile() {
        return new SqlTable(this, "additionalBinaryFile");
    }

    public SqlTable authorization() {
        return new SqlTable(this, "oreSiAuthorization");
    }

    public SqlTable authorizationAdditionalFiles() {
        return new SqlTable(this, "OreSiAuthorizationAdditionalFiles");
    }

    public SqlTable rightsRequest() {
        return new SqlTable(this, "RightsRequest");
    }

    public SqlTable forTableName(final String tableName) {
        return new SqlTable(this, tableName);
    }

    public static String requiredAuthorizationsAttributes(final Application app) {
        return app.getConfiguration().requiredAuthorizationsAttributes().stream()
                .map(s -> String.format("%s ltree[]", s))
                .collect(Collectors.joining(",\n"));
    }

    public static String publicRoleId() {
        return PUBLIC_UUID.toString();
    }

    private Stream<String> getAttributes() {
        return Optional.of(application.findData())
                .map(d -> d.entrySet()
                        .stream()
                        .map(entry -> Optional.ofNullable(entry)
                                .map(e -> e.getValue().submission())
                                .map(Submission::submissionScope)
                                .map(Submission.SubmissionScope::componentNames)
                                .orElse(null))
                        .filter(Objects::nonNull)
                        .flatMap(Set::stream)
                        .distinct())
                .orElse(Set.of("").stream());
    }

    public static String requiredAuthorizationsAttributesComparing(final Application app) {
        final String requiredAuthorizationsAttributesComparing = app.getConfiguration().requiredAuthorizationsAttributes().stream()
                .map(attribute -> String.format(
                        "((authorized).requiredAuthorizations.%1$s is null or (COALESCE((authorized).requiredAuthorizations.%1$s, ''::ltree) @> COALESCE((\"authorization\").requiredAuthorizations.%1$s, ''::ltree)))",
                        attribute
                ))
                .collect(Collectors.joining("\n AND "));
        return requiredAuthorizationsAttributesComparing + (Strings.isNullOrEmpty(requiredAuthorizationsAttributesComparing) ? "" : " AND\n ");
    }
}