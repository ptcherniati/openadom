package fr.inra.oresing.domain.data.deposit.validation.transformer;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.SomethingThatCanProvideEvaluationContext;
import fr.inra.oresing.domain.application.configuration.Ltree;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public record CodifyOneLineElementTransformer(CheckerTarget target) implements TransformOneLineElementTransformer {

    @Override
    public FieldType transform(final SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext, final FieldType value) {
        final FieldType valueAfterCodification;
        if (value == null || Strings.isNullOrEmpty(value.toString())) {
            valueAfterCodification = value;
        } else if (value instanceof ListType) {
            final List<StringType> collect = Arrays.stream(value.toString().split(","))
                    .map(Ltree::escapeToLabel)
                    .map(StringType::getStringTypeFromStringValue)
                    .collect(Collectors.toList());
            return ListType.getListTypeFromListValue(collect);
        } else {
            valueAfterCodification = StringType.getStringTypeFromStringValue(Ltree.escapeToLabel(value.toString()));
        }
        return valueAfterCodification;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("target", target)
                .toString();
    }
}