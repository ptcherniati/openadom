package fr.inra.oresing.rest.model.data.query;

import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.checker.*;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;

import java.util.Optional;

public sealed interface ComponentType permits
        ComponentType.ComponentTextType,
        ComponentType.ComponentReferenceType,
        ComponentType.ComponentNumericType,
        ComponentType.ComponentBooleanType,
        ComponentType.ComponentDateType {

    static fr.inra.oresing.domain.data.read.query.ComponentType getComponentType(final ComponentDescription componentDescription) {
        final CheckerDescription checkerDescription = Optional.ofNullable(componentDescription)
                .map(ComponentDescription::checker)
                .orElse(null);
        if (checkerDescription == null) {
            return new fr.inra.oresing.domain.data.read.query.ComponentTextType();
        }
        return switch (checkerDescription) {
            case ReferenceChecker ignored -> new fr.inra.oresing.domain.data.read.query.ComponentReferenceType();
            case IntegerChecker ignored-> new fr.inra.oresing.domain.data.read.query.ComponentNumericType();
            case FloatChecker ignored-> new fr.inra.oresing.domain.data.read.query.ComponentNumericType();
            case BooleanChecker ignored-> new fr.inra.oresing.domain.data.read.query.ComponentBooleanType();
            case DateChecker dateChecker-> {
                    String pattern = Optional.of(dateChecker)
                            .map(DateChecker::pattern)
                            .orElseThrow(() -> new BadDownloadDatasetQuery(BadDownloadDatasetQuery.MISSING_FORMAT_FOR_FILTER));
                    yield switch (DatePattern.of(pattern).getFieldType()){
                        case DATE -> new fr.inra.oresing.domain.data.read.query.ComponentDateType(
                                pattern,
                                DownloadDatasetQueryAdvancedSearch.FieldType.date);
                        case TIME -> new fr.inra.oresing.domain.data.read.query.ComponentDateType(
                                pattern,
                                DownloadDatasetQueryAdvancedSearch.FieldType.time);
                        case DATETIME -> new fr.inra.oresing.domain.data.read.query.ComponentDateType(
                                pattern,
                                DownloadDatasetQueryAdvancedSearch.FieldType.datetime);
                    };
            }
            default -> new fr.inra.oresing.domain.data.read.query.ComponentTextType();
        };
    }

    record ComponentTextType() implements ComponentType {
    }

    record ComponentReferenceType() implements ComponentType {
    }

    record ComponentBooleanType() implements ComponentType {
    }

    record ComponentNumericType() implements ComponentType {
    }

    record ComponentDateType(String format,
                                     DownloadDatasetQueryAdvancedSearch.FieldType fieldType) implements ComponentType {
    }
}
