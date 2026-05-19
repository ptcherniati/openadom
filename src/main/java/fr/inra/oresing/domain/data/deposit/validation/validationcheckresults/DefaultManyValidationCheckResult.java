package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnMultipleValue;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.LinkedLines;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Value
public class DefaultManyValidationCheckResult extends LinkedList<ValidationCheckResult> implements CheckerValidationCheckResult<ListType<FieldType<?>>>, ValidationCheckResult {
    ListType<FieldType<?>> value;
    CheckerTarget target;

    public DefaultManyValidationCheckResult(final Collection<? extends ValidationCheckResult> validationsCheckresult, final CheckerTarget target) {
        super(validationsCheckresult);
        value = buildValueFromFieldType(validationsCheckresult);
        this.target = target;
    }

    @SuppressWarnings("unchecked")
    private static ListType<FieldType<?>> buildValueFromFieldType(final Collection<? extends ValidationCheckResult> validationsCheckresult) {
        if (CollectionUtils.isEmpty(validationsCheckresult)) {
            return new ListType<>(NullType.INSTANCE);
        }
        final List<FieldType<?>> allValues = new ArrayList<>();
        for (final ValidationCheckResult vcr : validationsCheckresult) {
            if (vcr instanceof CheckerValidationCheckResult<?> cvr && cvr.value() instanceof FieldType<?> ft) {
                allValues.add(ft);
            }
        }
        if (allValues.isEmpty()) {
            return new ListType<>(NullType.INSTANCE);
        }
        final ListType<FieldType<?>> result = new ListType<>(allValues.get(0));
        result.getValue().addAll(allValues);
        return result;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public DataColumnValue<?, ?> transform(final LineChecker<?> lineChecker, final DataColumnValue<?, ?> referenceColumnRawValue, final DataColumn dataColumn, final Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo) {
        return switch (lineChecker.underlyingType()) {
            case ReferenceType ignored -> new DataColumnMultipleValue<>(
                    value().getValue().stream()
                            .map(referenceType -> {
                                referenceType.transform(lineChecker, referenceColumnRawValue, dataColumn, refsLinkedTo);
                                return referenceType;
                            })
                            .map(FieldType::getValue)
                            .map(Object::toString)
                            .map(StringType::getStringTypeFromStringValue)
                            .toList()
            );
            default -> new DataColumnMultipleValue<>(value().getValue());
        };
    }

    @Override
    public CheckerTarget target() {
        return target;
    }

    @Override
    @JsonIgnore
    public List<ValidationCheckResult> getValidations() {
        return this;
    }

    @Override
    public ValidationLevel level() {
        return stream()
                .anyMatch(ValidationCheckResult::isError) ? ValidationLevel.ERROR : ValidationLevel.SUCCESS;
    }

    @Override
    public String message() {
        return stream()
                .map(ValidationCheckResult::message)
                .collect(Collectors.joining(";"));
    }

    @Override
    public Map<String, Object> messageParams() {
        final Map<String, Object> messagesParams = new HashMap<>();
        for (final ValidationCheckResult validationCheckResult : this) {
            final Map<String, Object> map = validationCheckResult.messageParams();
            map.forEach((key, value1) -> ((List<Object>) messagesParams
                    .computeIfAbsent(key, k -> new LinkedList<>()))
                    .add(value1));
        }
        return messagesParams;

    }

    @Override
    public ListType<FieldType<?>> value() {
        return value;
    }
}