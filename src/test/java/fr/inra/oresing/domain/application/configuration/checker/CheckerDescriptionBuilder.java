package fr.inra.oresing.domain.application.configuration.checker;

import fr.inra.oresing.domain.checker.Multiplicity;

import java.time.temporal.TemporalAccessor;
import java.util.Set;

public class CheckerDescriptionBuilder {

    public static StringCheckerBuilder stringChecker() {
        return new StringCheckerBuilder();
    }

    public static IntegerCheckerBuilder integerChecker() {
        return new IntegerCheckerBuilder();
    }

    public static FloatCheckerBuilder floatChecker() {
        return new FloatCheckerBuilder();
    }

    public static DateCheckerBuilder dateChecker() {
        return new DateCheckerBuilder();
    }

    public static BooleanCheckerBuilder booleanChecker() {
        return new BooleanCheckerBuilder();
    }

    public static ReferenceCheckerBuilder referenceChecker() {
        return new ReferenceCheckerBuilder();
    }

    public static ComputationCheckerBuilder computationChecker() {
        return new ComputationCheckerBuilder();
    }

    public static GroovyExpressionCheckerBuilder groovyExpressionChecker() {
        return new GroovyExpressionCheckerBuilder();
    }

    public static class StringCheckerBuilder {
        private Multiplicity multiplicity = Multiplicity.ONE;
        private boolean required = false;
        private String pattern = ".*";

        public StringCheckerBuilder multiplicity(Multiplicity multiplicity) {
            this.multiplicity = multiplicity;
            return this;
        }

        public StringCheckerBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public StringCheckerBuilder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public StringChecker build() {
            return new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker, multiplicity, required, pattern);
        }
    }

    public static class IntegerCheckerBuilder {
        private Multiplicity multiplicity = Multiplicity.ONE;
        private boolean required = false;
        private Integer min = Integer.MIN_VALUE;
        private Integer max = Integer.MAX_VALUE;

        public IntegerCheckerBuilder multiplicity(Multiplicity multiplicity) {
            this.multiplicity = multiplicity;
            return this;
        }

        public IntegerCheckerBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public IntegerCheckerBuilder min(Integer min) {
            this.min = min;
            return this;
        }

        public IntegerCheckerBuilder max(Integer max) {
            this.max = max;
            return this;
        }

        public IntegerChecker build() {
            return new IntegerChecker(CheckerDescription.CheckerDescriptionType.IntegerChecker, multiplicity, required, min, max);
        }
    }

    public static class FloatCheckerBuilder {
        private Multiplicity multiplicity = Multiplicity.ONE;
        private boolean required = false;
        private Float min = Float.MIN_VALUE;
        private Float max = Float.MAX_VALUE;

        public FloatCheckerBuilder multiplicity(Multiplicity multiplicity) {
            this.multiplicity = multiplicity;
            return this;
        }

        public FloatCheckerBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public FloatCheckerBuilder min(Float min) {
            this.min = min;
            return this;
        }

        public FloatCheckerBuilder max(Float max) {
            this.max = max;
            return this;
        }

        public FloatChecker build() {
            return new FloatChecker(CheckerDescription.CheckerDescriptionType.FloatChecker, multiplicity, required, min, max);
        }
    }

    public static class DateCheckerBuilder {
        private Multiplicity multiplicity = Multiplicity.ONE;
        private boolean required = false;
        private String pattern = "dd/MM/yyyy";
        private TemporalAccessor min;
        private TemporalAccessor max;
        private String duration;

        public DateCheckerBuilder multiplicity(Multiplicity multiplicity) {
            this.multiplicity = multiplicity;
            return this;
        }

        public DateCheckerBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public DateCheckerBuilder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public DateCheckerBuilder min(TemporalAccessor min) {
            this.min = min;
            return this;
        }

        public DateCheckerBuilder max(TemporalAccessor max) {
            this.max = max;
            return this;
        }

        public DateCheckerBuilder duration(String duration) {
            this.duration = duration;
            return this;
        }

        public DateChecker build() {
            return new DateChecker(CheckerDescription.CheckerDescriptionType.DateChecker, multiplicity, required, pattern, min, max, duration);
        }
    }

    public static class BooleanCheckerBuilder {
        private Multiplicity multiplicity = Multiplicity.ONE;
        private boolean required = false;
        private boolean isTrue = false;

        public BooleanCheckerBuilder multiplicity(Multiplicity multiplicity) {
            this.multiplicity = multiplicity;
            return this;
        }

        public BooleanCheckerBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public BooleanCheckerBuilder isTrue(boolean isTrue) {
            this.isTrue = isTrue;
            return this;
        }

        public BooleanChecker build() {
            return new BooleanChecker(CheckerDescription.CheckerDescriptionType.BooleanChecker, multiplicity, required, isTrue);
        }
    }

    public static class ReferenceCheckerBuilder {
        private String componentKey;
        private Multiplicity multiplicity = Multiplicity.ONE;
        private boolean required = false;
        private String refType;
        private boolean isRecursive = false;
        private boolean isParent = false;

        public ReferenceCheckerBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public ReferenceCheckerBuilder multiplicity(Multiplicity multiplicity) {
            this.multiplicity = multiplicity;
            return this;
        }

        public ReferenceCheckerBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public ReferenceCheckerBuilder refType(String refType) {
            this.refType = refType;
            return this;
        }

        public ReferenceCheckerBuilder isRecursive(boolean isRecursive) {
            this.isRecursive = isRecursive;
            return this;
        }

        public ReferenceCheckerBuilder isParent(boolean isParent) {
            this.isParent = isParent;
            return this;
        }

        public ReferenceChecker build() {
            return new ReferenceChecker(CheckerDescription.CheckerDescriptionType.ReferenceChecker, componentKey, multiplicity, required, refType, isRecursive, isParent);
        }
    }

    public static class ComputationCheckerBuilder {
        private Multiplicity multiplicity = Multiplicity.ONE;
        private boolean required = false;
        private String expression;
        private Set<String> references;
        private Set<String> exceptionMessages;

        public ComputationCheckerBuilder multiplicity(Multiplicity multiplicity) {
            this.multiplicity = multiplicity;
            return this;
        }

        public ComputationCheckerBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public ComputationCheckerBuilder expression(String expression) {
            this.expression = expression;
            return this;
        }

        public ComputationCheckerBuilder references(Set<String> references) {
            this.references = references;
            return this;
        }

        public ComputationCheckerBuilder exceptionMessages(Set<String> exceptionMessages) {
            this.exceptionMessages = exceptionMessages;
            return this;
        }

        public ComputationChecker build() {
            return new ComputationChecker(CheckerDescription.CheckerDescriptionType.ComputationChecker, multiplicity, required, expression, references, exceptionMessages);
        }
    }

    public static class GroovyExpressionCheckerBuilder {
        private Multiplicity multiplicity = Multiplicity.ONE;
        private boolean required = false;
        private String expression;
        private Set<String> references;
        private Set<String> exceptionMessages;

        public GroovyExpressionCheckerBuilder multiplicity(Multiplicity multiplicity) {
            this.multiplicity = multiplicity;
            return this;
        }

        public GroovyExpressionCheckerBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public GroovyExpressionCheckerBuilder expression(String expression) {
            this.expression = expression;
            return this;
        }

        public GroovyExpressionCheckerBuilder references(Set<String> references) {
            this.references = references;
            return this;
        }

        public GroovyExpressionCheckerBuilder exceptionMessages(Set<String> exceptionMessages) {
            this.exceptionMessages = exceptionMessages;
            return this;
        }

        public GroovyExpressionChecker build() {
            return new GroovyExpressionChecker(CheckerDescription.CheckerDescriptionType.GroovyExpressionChecker, multiplicity, required, expression, references, exceptionMessages);
        }
    }
}