package fr.inra.oresing.transformer;

import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.model.SomethingThatCanProvideEvaluationContext;
import fr.inra.oresing.persistence.Ltree;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.assertj.core.util.Strings;

public class CodifyOneLineElementTransformer implements TransformOneLineElementTransformer {

    private final CheckerTarget target;

    public CodifyOneLineElementTransformer(CheckerTarget target) {
        this.target = target;
    }

    @Override
    public CheckerTarget getTarget() {
        return target;
    }

    @Override
    public String transform(SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext, String value) {
        String valueAfterCodification;
        if (Strings.isNullOrEmpty(value)) {
            valueAfterCodification = value;
        } else {
            valueAfterCodification = Ltree.escapeToLabel(value);
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