package fr.inra.oresing.domain.application.configuration.checker;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.repository.data.DataRepository;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public sealed interface CheckerDescription permits
        BooleanChecker,
        ComputationChecker,
        DateChecker,
        FloatChecker,
        GroovyExpressionChecker,
        IntegerChecker,
        ReferenceChecker,
        StringChecker {
    CheckerDescription NO_CHECKER = new StringChecker(CheckerDescriptionType.StringChecker, Multiplicity.ONE, false, ".*");

    static ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> getUUidByNaturalKey(final ImmutableMap<DataValue.LineIdentityColumnName, UUID> referenceIdPerKeys) {
        return ImmutableMap.copyOf(
                referenceIdPerKeys.entrySet().stream()
                        .collect(
                                Collectors.groupingBy(
                                        e -> e.getKey(),
                                        Collectors.mapping(
                                                Map.Entry::getValue,
                                                ImmutableSet.toImmutableSet()
                                        )
                                )
                        )
        );
    }

    CheckerDescriptionType type();

    Multiplicity multiplicity();

    boolean required();

    default <F extends FieldType<?>> F buildFieldtype(final DataRepository repository, final PublishContext.PublishContextBuilder publishContextBuilder, final CheckerTarget target, final LineChecker.LineTransformer transformer) {
        return (F) switch (this) {
            case null -> NullType.INSTANCE;
            case final ReferenceChecker referenceChecker -> {
                final ImmutableMap<DataValue.LineIdentityColumnName, UUID> referenceIdPerKeys = repository.getDataIdPerKeys(referenceChecker.refType());
                final ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues = getUUidByNaturalKey(referenceIdPerKeys);
                yield new ReferenceType(target, referenceChecker.refType(), referenceValues, transformer, null);
            }
            case final DateChecker dateChecker ->
                    new DateType(dateChecker.pattern(), dateChecker.duration(), dateChecker.min(), dateChecker.max());
            case final BooleanChecker booleanChecker -> new BooleanType(false);
            case final FloatChecker floatChecker -> {
                final Float minFloat = floatChecker.min();
                final Float maxFloat = floatChecker.max();
                yield new FloatType(minFloat, maxFloat);
            }
            case final IntegerChecker integerChecker -> {
                final Integer minInteger = integerChecker.min();
                final Integer maxInteger = integerChecker.max();
                yield new IntegerType(minInteger, maxInteger);
            }
            case final GroovyExpressionChecker groovy -> {
                final String expression = groovy.expression();
                yield new BooleanType(expression);
            }
            case final StringChecker stringChecker -> new StringType(stringChecker.pattern());
            case ComputationChecker _ -> new StringType("");
        };
    }

    default String comment() {
        return "String";
    }

    default String buildImportDataExempleForheader() {
        return "a string";
    }

    enum CheckerDescriptionType {
        BooleanChecker,
        ComputationChecker,
        DateChecker,
        FloatChecker,
        GroovyExpressionChecker,
        IntegerChecker,
        ReferenceChecker,
        StringChecker
    }

    record ReferenceValueDecorator(DataValue decorated) {

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
}