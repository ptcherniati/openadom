package fr.inra.oresing.domain.application.configuration.checker;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;

import java.time.format.DateTimeFormatter;
import java.util.*;
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


    CheckerDescriptionType type();

    Multiplicity multiplicity();

    boolean required();

    default <FT extends FieldType> FT buildFieldtype(final DataRepository repository, final PublishContext.PublishContextBuilder publishContextBuilder, final CheckerTarget target, final LineChecker.LineTransformer transformer) {
        return (FT) switch (this) {
            case final ReferenceChecker referenceChecker -> {
                final ImmutableMap<DataValue.LineIdentityPatternColumnName, UUID> referenceIdPerKeys = repository.getDataIdPerKeys(referenceChecker.refType());
                final ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues = getUUidByNaturalKey(referenceIdPerKeys);
                yield new ReferenceType(target, referenceChecker.refType(), referenceValues, transformer);
            }
            case final DateChecker dateChecker ->
                    new DateType(dateChecker.pattern(), DateTimeFormatter.ofPattern(dateChecker.pattern()), dateChecker.duration(), dateChecker.min(), dateChecker.max());
            case final BooleanChecker booleanChecker -> new BooleanType(false);
            case final FloatChecker floatChecker -> {
                final Float minFloat = Optional.ofNullable(floatChecker.min())
                        .orElse(null);
                final Float maxFloat = Optional.ofNullable(floatChecker.max())
                        .orElse(null);
                yield new FloatType(minFloat, maxFloat);
            }
            case final IntegerChecker integerChecker -> {
                final Integer minInteger = Optional.ofNullable(integerChecker.min())
                        .orElse(null);
                final Integer maxInteger = Optional.ofNullable(integerChecker.max())
                        .orElse(null);
                yield new IntegerType(minInteger, maxInteger);
            }
            case final GroovyExpressionChecker groovy -> {
                final String expression = groovy.expression();
                final Set<String> references = groovy.references();
                yield new BooleanType(expression);
            }
            case final StringChecker stringChecker -> new StringType(stringChecker.pattern());
            default -> new StringType("");
        };
    }

    
    private static ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> getUUidByNaturalKey(final ImmutableMap<DataValue.LineIdentityPatternColumnName, UUID> referenceIdPerKeys) {
        return ImmutableMap.copyOf(
                referenceIdPerKeys.entrySet().stream()
                        .collect(
                                Collectors.groupingBy(
                                        e-> e.getKey().identity(),
                                        Collectors.mapping(
                                                Map.Entry::getValue,
                                                ImmutableSet.toImmutableSet()
                                        )
                                )
                        )
        );
    }

    default String comment(){
        return "String";
    }

    default String buildImportDataExempleForheader(){
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
        StringChecker;
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
