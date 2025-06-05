package fr.inra.oresing.domain.exceptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fr.inra.oresing.domain.application.Application;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Map;
import java.util.Optional;

@EqualsAndHashCode(callSuper = true)
@Value
@JsonIgnoreProperties({"stackTrace", "detailMassage", "cause", "depth", "suppressedExeceptions"})
public class
SiOreIllegalArgumentException extends IllegalArgumentException {
    public static final String NOT_GIVEN = "not given";
    public static final String NO_RIGHT_ON_TABLE = "noRightOnTable";
    public static final String NO_RIGHT_ON_TABLE_FOR_DEPOSIT = "noRightsForDeposit";
    public static final String NO_RIGHT_ON_TABLE_FOR_PUBLISH_OR_UNPUBLISH = "noRightForPublish";
    public static final String NO_RIGHT_ON_TABLE_FOR_DELETE = "noRightForDelete";
    public static final String TABLE = "table";
    public static final String NO_FILE_To_DELETE = "noFileToDelete";
    public static final String MISSING_DATA = "missingData";
    String message;
    Map<String, Object> params;

    public static void testExistsData(Application application, String dataName) {
        if (application.getConfiguration().dataDescription().containsKey(dataName)) {
            return;
        }
        throw new SiOreIllegalArgumentException(
                MISSING_DATA,
                Map.of(
                        "dataName", dataName,
                        "application", application.getName()
                )
        );
    }

    public static SiOreIllegalArgumentException noRightOnTable(String table) {
        return new SiOreIllegalArgumentException(NO_RIGHT_ON_TABLE, Map.of(TABLE, table));
    }

    public static SiOreIllegalArgumentException noRightOnTableForPublishOrUnpublish(String table) {
        return new SiOreIllegalArgumentException(NO_RIGHT_ON_TABLE_FOR_PUBLISH_OR_UNPUBLISH, Map.of(TABLE, table));
    }

    public static SiOreIllegalArgumentException noRightOnTableForDeposit(String table) {
        return new SiOreIllegalArgumentException(NO_RIGHT_ON_TABLE_FOR_DEPOSIT, Map.of(TABLE, table));
    }

    public static SiOreIllegalArgumentException noRightOnTableForDelete(String table) {
        return new SiOreIllegalArgumentException(NO_RIGHT_ON_TABLE_FOR_DELETE, Map.of(TABLE, table));
    }

    public static SiOreIllegalArgumentException noRightOnTableForDelete(SiOreIllegalArgumentException illegalArgumentException) {
        return noRightOnTableForDelete(Optional.ofNullable(illegalArgumentException.getParams())
                .map(map -> map.get(SiOreIllegalArgumentException.TABLE))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(NOT_GIVEN)
        );
    }

    public static SiOreIllegalArgumentException noRightOnTableForPublishOrUnpublish(SiOreIllegalArgumentException illegalArgumentException) {
        return noRightOnTableForPublishOrUnpublish(Optional.ofNullable(illegalArgumentException.getParams())
                .map(map -> map.get(SiOreIllegalArgumentException.TABLE))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(NOT_GIVEN)
        );
    }

    public static SiOreIllegalArgumentException noRightOnTableForDeposit(SiOreIllegalArgumentException illegalArgumentException) {
        return noRightOnTableForDeposit(Optional.ofNullable(illegalArgumentException.getParams())
                .map(map -> map.get(SiOreIllegalArgumentException.TABLE))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .orElse(NOT_GIVEN)
        );
    }
}