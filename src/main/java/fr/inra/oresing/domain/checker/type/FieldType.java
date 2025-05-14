package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.persistence.SqlPrimitiveType;

import java.io.IOException;
import java.util.Map;

public sealed interface FieldType<T> extends SomethingToBeStoredAsJsonInDatabase, SomethingToBeSentToFrontend
        permits AbstractType,
        BooleanType,
        DateType,
        FloatType,
        IntegerType,
        ListType,
        MapType,
        PatternType,
        NullType,
        StringType {

    T getValue();

    SqlPrimitiveType getSqlType();

    CheckerValidationCheckResult check(String value, LineChecker lineChecker);

    FieldType<T> copy();

    void serialize(JsonGenerator gen) throws IOException;

    default DataColumnValue transform(final LineChecker lineChecker,
                                      final DataColumnValue referenceColumnRawValue,
                                      final DataColumn referenceColumn,
                                      final Map<String, Map<String, RefsLinkedToValue>> refsLinkedToBuilder) {
        return referenceColumnRawValue;
    }

    void serialize(JsonGenerator gen, String key) throws IOException;

    void serialize(ObjectNode rootNode, ObjectMapper mapper, String key);

    void serializeAddArray(ArrayNode arrayNode);

    default String toStringForComponentValue(){
        return toString();
    }

    default CheckerValidationCheckResult postTreatment(CheckerValidationCheckResult checkerValidationCheckResult){
        return checkerValidationCheckResult;
    }
}