package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnMultipleValue;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import lombok.EqualsAndHashCode;
import lombok.Value;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Value
public class DefaultManyValidationCheckResult extends LinkedList<ValidationCheckResult> implements CheckerValidationCheckResult, ValidationCheckResult {
    ListType value;
    CheckerTarget target;

    public DefaultManyValidationCheckResult(final Collection<? extends ValidationCheckResult> validationsCheckresult, final CheckerTarget target) {
        super(validationsCheckresult);
        value = buildValueFromFieldType(validationsCheckresult);
        this.target = target;
    }

    private static ListType buildValueFromFieldType(final Collection<? extends ValidationCheckResult> validationsCheckresult) {
        if (CollectionUtils.isEmpty(validationsCheckresult)) {
            return new ListType<>(new NullType());
        }
        return validationsCheckresult.stream()
                .findFirst()
                .filter(CheckerValidationCheckResult.class::isInstance)
                .map(CheckerValidationCheckResult.class::cast)
                .map(CheckerValidationCheckResult::value)
                .map(ListType::new)
                .map(lt -> {
                            lt.getValue().addAll(
                                    validationsCheckresult.stream()
                                            .filter(CheckerValidationCheckResult.class::isInstance)
                                            .map(CheckerValidationCheckResult.class::cast)
                                            .map(CheckerValidationCheckResult::value)
                                            .toList()
                            );
                            return lt;
                        }
                )
                .orElse(new ListType<>(new NullType()));
    }

    @Override
    public DataColumnValue transform(final LineChecker lineChecker, final DataColumnValue referenceColumnRawValue, final DataColumn dataColumn, final Map refsLinkedTo) {
        return switch (lineChecker.underlyingType()) {
            case ReferenceType ignored -> new DataColumnMultipleValue(
                            ((List<FieldType>) value().getValue()).stream()
                                    .map(referenceType -> {
                                        referenceType.transform(lineChecker, referenceColumnRawValue, dataColumn, refsLinkedTo);
                                        return referenceType;
                                    })
                                    .map(FieldType::getValue)
                                    .map(Object::toString)
                                    .map(StringType::getStringTypeFromStringValue)
                                    .toList()
            );
            default -> new DataColumnMultipleValue((List) value().getValue());
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
                .anyMatch(vcr -> vcr.isError()) ? ValidationLevel.ERROR : ValidationLevel.SUCCESS;
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
            map.entrySet()
                    .forEach(entry -> {
                        ((List) messagesParams
                                .computeIfAbsent(entry.getKey(), k -> new LinkedList<>()))
                                .add(entry.getValue());
                    });
        }
        return messagesParams;

    }

    @Override
    public FieldType value() {
        return value;
    }
}