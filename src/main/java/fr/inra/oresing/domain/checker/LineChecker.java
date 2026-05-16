package fr.inra.oresing.domain.checker;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.GroovyExpressionChecker;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.*;
import fr.inra.oresing.domain.groovy.BooleanGroovyExpression;
import fr.inra.oresing.domain.groovy.Expression;
import fr.inra.oresing.domain.groovy.GroovyDecorator;
import fr.inra.oresing.domain.groovy.StringGroovyExpression;
import fr.inra.oresing.domain.groovy.exception.GroovyException;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;
import org.apache.commons.lang3.NotImplementedException;

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public sealed interface LineChecker<F extends FieldType<?>> permits LineChecker.ManyChecker, LineChecker.OneChecker {


    @SuppressWarnings({"unchecked", "java:S3740"})
    static <L extends FieldType<?>> Set<LineChecker<L>> toLineChecker(
            DataRepository referenceValueRepository,
            PublishContext.PublishContextBuilder publishContextBuilder,
            TransformationConfiguration transformation,
            String componentKey,
            CheckerDescription checker) {
        final DataColumn target = new DataColumn(componentKey);
        final LineChecker.LineTransformer lineTransformer = transformation == null ?
                LineChecker.LineTransformer.NULL_LINE_TRANSFORMER :
                LineChecker.LineTransformer.newTransformer(
                        referenceValueRepository,
                        transformation,
                        target,
                        publishContextBuilder,
                        Optional.ofNullable(checker)
                                .map(CheckerDescription::multiplicity)
                                .orElse(transformation.multiplicity())
                );
        final L fieldType = Objects.requireNonNull(checker).buildFieldtype(
                referenceValueRepository,
                publishContextBuilder,
                target,
                lineTransformer
        );
        return switch (checker.multiplicity()) {
            case ONE -> Set.of(new OneChecker<L>(
                    fieldType,
                    target,
                    lineTransformer,
                    checker
            ));
            case MANY -> Set.of((LineChecker<L>) new ManyChecker<>(
                    new ListType<>(fieldType),
                    target,
                    lineTransformer,
                    checker
            ));
        };
    }

    @SuppressWarnings({"unchecked", "java:S3740"})
    default F underlyingType() {
        return (F) switch (this) {
            case final ManyChecker<?, ?> manyChecker -> manyChecker.fieldTypeForOne;
            case final OneChecker<?> oneChecker -> oneChecker.fieldTypeForOne;
        };
    }

    DataColumn target();

    LineTransformer transformer();

    CheckerValidationCheckResult<?> check(String value);

    CheckerDescription checkerDescription();

    F fieldTypeForOne();

    default CheckerValidationCheckResult<?> checkRequiredThenCheck(final StringType value) {
        return checkRequiredThenCheck(value.toString());
    }

    default CheckerValidationCheckResult<?> checkReference(final DataDatum referenceDatum, Map<String, Object> context) {
        final DataDatum transformedReferenceDatum = transformer().transform(referenceDatum, context);
        DataColumn column = target();
        FieldType<?> valuesToCheck = transformedReferenceDatum.getValuesToCheck(column);
        return Optional.ofNullable(valuesToCheck)
                .map(Object::toString)
                .map(this::checkRequiredThenCheck)
                .orElse(DefaultCheckerValidationCheckResult.error("i don't know", ImmutableMap.of(), null));
    }

    default CheckerValidationCheckResult<?> checkRequiredThenCheck(final String value) {
        final CheckerValidationCheckResult<?> validationCheckResult;
        if (Strings.isNullOrEmpty(value)) {
            if (checkerDescription().required()) {
                final DataColumn target = target();
                validationCheckResult = DefaultCheckerValidationCheckResult.error(target.getInternationalizedKey("requiredValue"), ImmutableMap.of("component", target.column()), target);
            } else {
                validationCheckResult = DefaultCheckerValidationCheckResult.success(target(), new StringType(value));
            }
        } else {
            validationCheckResult = check(value);
        }
        return validationCheckResult;
    }

    LineChecker<?> copy();

    interface Transformer {
        DataDatum transform(DataDatum referenceDatum, Map<String, Object> context);
    }

    sealed interface LineTransformer extends Transformer permits
            LineTransformer.ChainTransformersLineTransformer,
            LineTransformer.NullLinetransformer,
            LineTransformer.TransformOneLineElementTransformer {
        NullLinetransformer NULL_LINE_TRANSFORMER = new NullLinetransformer();

        @SuppressWarnings({"java:S1172", "unused"})
        static LineTransformer newTransformer(final DataRepository referenceValueRepository,
                                              final TransformationConfiguration configuration,
                                              final DataColumn target,
                                              final PublishContext.PublishContextBuilder publishContextBuilder,
                                              final Multiplicity multiplicity) {
            final ImmutableList.Builder<LineTransformer> transformersBuilder = ImmutableList.builder();
            if (!configuration.expression().isEmpty()) {
                final String expression = configuration.expression();
                final Set<String> exceptionMessages = configuration.exceptionMessages();
                final Expression<?> groovyExpression;


                if (configuration instanceof GroovyExpressionChecker) {
                    groovyExpression = BooleanGroovyExpression.forExpression(expression, exceptionMessages);
                } else {
                    groovyExpression = StringGroovyExpression.forExpression(expression, exceptionMessages);
                }
                final Set<String> references = configuration.references();
                final TransformOneLineElementTransformer transformer =
                        new TransformOneLineElementTransformer.GroovyExpressionOnOneLineElementTransformer(
                                target,
                                groovyExpression,
                                new HashMap<>(),//groovyContext,
                                multiplicity,
                                references
                        );
                transformersBuilder.add(transformer);
            }
            final ImmutableList<LineTransformer> transformers = transformersBuilder.build();
            return new ChainTransformersLineTransformer(transformers);
        }

        sealed interface TransformOneLineElementTransformer extends LineTransformer permits TransformOneLineElementTransformer.GroovyExpressionOnOneLineElementTransformer {
            DataColumn target();

            @Override
            default DataDatum transform(final DataDatum referenceDatum, Map<String, Object> context) {
                final DataColumnValue<?, ?> referenceColumnValue;
                if (referenceDatum.contains(target())) {
                    referenceColumnValue = referenceDatum.get(target());
                } else {
                    // ici, on est dans le cas où on applique une transformation sur un colonne
                    // qui n'existe pas. Elle a été déclarée comme colonne devant subir une transformation
                    // alors que ce n'est pas une colonne du référentiel passé.
                    // Comme il faut quand même appliquer la transformation, on part de rien
                    referenceColumnValue = DataColumnSingleValue.empty();
                }
                if (this instanceof GroovyExpressionOnOneLineElementTransformer groovyExpressionOnOneLineElementTransformer) {
                    groovyExpressionOnOneLineElementTransformer.context.putAll(context);
                }

                final UnaryOperator<FieldType<?>> fn = _ -> transform(referenceDatum);
                final DataColumnValue<?, ?> transformedReferenceColumnValue = referenceColumnValue.transform(fn);
                final DataDatum transformedDatum = DataDatum.copyOf(referenceDatum);
                transformedDatum.put(target(), transformedReferenceColumnValue);
                return (this instanceof GroovyExpressionOnOneLineElementTransformer groovyExpressionOnOneLineElementTransformer) &&
                        groovyExpressionOnOneLineElementTransformer.multiplicity().equals(Multiplicity.ONE) ? referenceDatum : transformedDatum;
            }

            @SuppressWarnings("java:S1452")
            FieldType<?> transform(SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext);

            record GroovyExpressionOnOneLineElementTransformer(
                    DataColumn target,
                    Expression<?> groovyExpression,
                    Map<String, Object> context,
                    Multiplicity multiplicity,
                    Set<String> references
            ) implements TransformOneLineElementTransformer {

                @Override
                @SuppressWarnings({"java:S3740"})
                public FieldType<?> transform(final SomethingThatCanProvideEvaluationContext somethingThatCanProvideEvaluationContext) {
                    ImmutableMap<String, Object> result = ImmutableMap.<String, Object>builder()
                            .putAll(Stream.of(
                                            this.context,
                                            somethingThatCanProvideEvaluationContext.getEvaluationContext()
                                    )
                                    .flatMap(map -> map.entrySet().stream())
                                    .collect(Collectors.toMap(
                                            Map.Entry::getKey,
                                            Map.Entry::getValue,
                                            (_, v2) -> v2  // En cas de conflit, garder v2 (dernière valeur)
                                    )))
                            .build();

                    final Object evaluate = groovyExpression().evaluate(result);
                    if (evaluate instanceof Boolean bool) {
                        return BooleanType.of(bool);
                    }
                    return switch (multiplicity()) {
                        case ONE -> StringType.getStringTypeFromStringValue((String) evaluate);
                        case MANY -> Arrays.stream(((String) evaluate).split(","))
                                .map(StringType::getStringTypeFromStringValue)
                                .collect(
                                        ListType::ofStringType,
                                        ListType::add,
                                        ListType::merge
                                );
                    };
                }
            }

        }

        /**
         * On expose pas directement les entités dans le contexte Groovy mais on contrôle un peu les types
         */
        record ReferenceValueDecorator(DataValue decorated) implements GroovyDecorator {

            public String getHierarchicalKey() {
                return decorated.getHierarchicalKey().getSql();
            }

            public String getNaturalKey() {
                return decorated.getNaturalKey().getSql();
            }

            public Map<String, Object> getRefValues() {
                return decorated.getRefValues().toObjectsExposedInGroovyContext();
            }
        }

        record NullLinetransformer() implements LineTransformer {

            @Override
            public DataDatum transform(final DataDatum referenceDatum, Map<String, Object> context) {
                return referenceDatum;
            }
        }

        record ChainTransformersLineTransformer(List<LineTransformer> transformers) implements LineTransformer {
            @Override
            public DataDatum transform(final DataDatum referenceDatumBeforeTransformation, Map<String, Object> context) {
                final Deque<DataDatum> transformations = new LinkedList<>();
                transformations.add(referenceDatumBeforeTransformation);
                transformers().forEach(lineTransformer -> {
                    final DataDatum datumAfterLastTransformation = transformations.getLast();
                    final DataDatum datumAfterOneMoreTransformation = lineTransformer.transform(datumAfterLastTransformation, context);
                    transformations.add(datumAfterOneMoreTransformation);
                });
                return transformations.getLast();
            }
        }
    }

    record ManyChecker<F extends FieldType<?>, U extends ListType<F>>(
            U value,
            F fieldTypeForOne,
            DataColumn target,
            LineTransformer transformer,
            CheckerDescription checkerDescription
    ) implements LineChecker<F> {

        @SuppressWarnings("unchecked")
        public ManyChecker(final U value, final DataColumn target, final LineTransformer transformer, final CheckerDescription checkerDescription) {
            this(value, Optional.ofNullable(value).map(ListType::getFieldType).orElse((F) new StringType("")), target, transformer, checkerDescription);
        }

        @SuppressWarnings({"unchecked", "java:S3740"})
        public CheckerValidationCheckResult<?> check(final String value) {
            F copiedFieldType = (F) fieldTypeForOne().copy();
            ManyChecker<F, U> copiedChecker = new ManyChecker<>(value(), copiedFieldType, target(), transformer(), checkerDescription());
            return copiedFieldType.check(value, copiedChecker);
        }

        @Override
        @SuppressWarnings({"unchecked", "java:S3740"})
        public CheckerValidationCheckResult<?> checkReference(final DataDatum referenceDatum, Map<String, Object> context) {
            final DataDatum transformedReferenceDatum = transformer().transform(referenceDatum, context);
            DataColumn column = target();
            FieldType<?> valuesToCheck = transformedReferenceDatum.getValuesToCheck(column);

            return Optional.ofNullable(valuesToCheck)
                    .map(ListType.class::cast)
                    .map(ListType::getValue)
                    .map(list -> {
                                final List<CheckerValidationCheckResult<?>> validationCheckResults = list.stream()
                                        .map(o -> checkRequiredThenCheck((StringType) o))
                                        .toList();
                                return (CheckerValidationCheckResult<?>) new DefaultManyValidationCheckResult(validationCheckResults, column);
                            }
                    )
                    .orElseGet(() -> DefaultCheckerValidationCheckResult.error("noValueToCheck", ImmutableMap.of(
                            "column", column.column()
                    ), null));
        }

        @Override
        @SuppressWarnings({"unchecked", "java:S3740"})
        public LineChecker<F> copy() {
            return new ManyChecker<>(value(), (F) fieldTypeForOne().copy(), target(), transformer(), checkerDescription());
        }

    }

    record OneChecker<F extends FieldType<?>>(
            F fieldTypeForOne,
            DataColumn target,
            LineTransformer transformer,
            CheckerDescription checkerDescription
    ) implements LineChecker<F> {

        @Override
        public CheckerValidationCheckResult<?> checkReference(final DataDatum referenceDatum, Map<String, Object> context) {

            if (checkerDescription() instanceof GroovyExpressionChecker) {
                try {
                    DataDatum transformedReferenceDatum = transformer().transform(referenceDatum, context);
                    DataColumn column = target();
                    FieldType<?> valuesToCheck = transformedReferenceDatum.getValuesToCheck(column);
                    return valuesToCheck instanceof PatternType<?, ?> patternType ?
                            PatternValidationCheckResult.of(GroovyValidationCheckResult.success(target(), valuesToCheck), patternType)
                            : GroovyValidationCheckResult.success(target(), valuesToCheck);
                } catch (GroovyException groovyException) {
                    return GroovyValidationCheckResult.error(target(), groovyException.getMessage(), ImmutableMap.copyOf(groovyException.getParamsCopy()));
                }
            }
            final DataDatum transformedReferenceDatum = transformer().transform(referenceDatum, context);
            DataColumn column = target();
            FieldType<?> valuesToCheck = transformedReferenceDatum.getValuesToCheck(column);
            return Optional.ofNullable(valuesToCheck)
                    .map(FieldType::toStringForComponentValue)
                    .map(this::checkRequiredThenCheck)
                    .map(Objects.requireNonNull(valuesToCheck)::postTreatment)
                    .orElseThrow(() -> new NotImplementedException("I don't know"));
        }

        @SuppressWarnings({"unchecked", "java:S3740"})
        public LineChecker<F> copy() {
            return new OneChecker<>((F) fieldTypeForOne().copy(), target(), transformer(), checkerDescription());
        }

        @SuppressWarnings({"unchecked", "java:S3740"})
        public CheckerValidationCheckResult<?> check(final String value) {
            F copiedFieldType = (F) fieldTypeForOne().copy();
            OneChecker<F> copiedChecker = new OneChecker<>(copiedFieldType, target(), transformer(), checkerDescription());
            return copiedFieldType.check(value, copiedChecker);
        }

    }
}