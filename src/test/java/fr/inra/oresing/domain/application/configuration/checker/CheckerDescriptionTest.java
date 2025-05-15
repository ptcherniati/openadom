package fr.inra.oresing.domain.application.configuration.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.repository.data.DataRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CheckerDescriptionTest {

    @Mock
    DataRepository repository;
    @Mock
    PublishContext.PublishContextBuilder publishContextBuilder;
    @Mock
    CheckerTarget target;
    LineChecker.LineTransformer transformer = LineChecker.LineTransformer.NULL_LINE_TRANSFORMER;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testCommentIspresent() {
        final BooleanChecker booleanChecker = new BooleanChecker(CheckerDescription.CheckerDescriptionType.BooleanChecker, Multiplicity.ONE, true, true);
        booleanChecker.comment();
        assertEquals("Boolean", booleanChecker.comment());
        final DateChecker dateChecker = new DateChecker(CheckerDescription.CheckerDescriptionType.DateChecker, Multiplicity.ONE, true, "yyyy-MM-dd", null, null, null);
        assertEquals("yyyy-MM-dd Date", dateChecker.comment());
        final FloatChecker floatChecker = new FloatChecker(CheckerDescription.CheckerDescriptionType.FloatChecker, Multiplicity.ONE, true, 1.0f, 10.0f);
        assertEquals("Float", floatChecker.comment());
        final IntegerChecker integerChecker = new IntegerChecker(CheckerDescription.CheckerDescriptionType.IntegerChecker, Multiplicity.ONE, true, 1, 100);
        assertEquals("Integer", integerChecker.comment());
        final ReferenceChecker referenceChecker = Mockito.mock(ReferenceChecker.class, Mockito.CALLS_REAL_METHODS);
        Mockito.when(referenceChecker.refType()).thenReturn("referenceType");
        assertEquals("referenceType Reference", referenceChecker.comment());
        final GroovyExpressionChecker groovyExpressionChecker = Mockito.mock(GroovyExpressionChecker.class, Mockito.CALLS_REAL_METHODS);
        assertEquals("String", groovyExpressionChecker.comment());
        final StringChecker stringChecker = Mockito.mock(StringChecker.class, Mockito.CALLS_REAL_METHODS);
        assertEquals("String", stringChecker.comment());
    }

    @Test
    void testBuildImportDataExempleForheader() {
        final BooleanChecker booleanChecker = new BooleanChecker(CheckerDescription.CheckerDescriptionType.BooleanChecker, Multiplicity.ONE, true, true);
        booleanChecker.comment();
        assertEquals("a boolean", booleanChecker.buildImportDataExempleForheader());
        final DateChecker dateChecker = new DateChecker(CheckerDescription.CheckerDescriptionType.DateChecker, Multiplicity.ONE, true, "yyyy-MM-dd", null, null, null);
        assertEquals("a date with pattern yyyy-MM-dd", dateChecker.buildImportDataExempleForheader());
        final FloatChecker floatChecker = new FloatChecker(CheckerDescription.CheckerDescriptionType.FloatChecker, Multiplicity.ONE, true, 1.0f, 10.0f);
        assertEquals("a float", floatChecker.buildImportDataExempleForheader());
        final IntegerChecker integerChecker = new IntegerChecker(CheckerDescription.CheckerDescriptionType.IntegerChecker, Multiplicity.ONE, true, 1, 100);
        assertEquals("an integer", integerChecker.buildImportDataExempleForheader());
        final ReferenceChecker referenceChecker = Mockito.mock(ReferenceChecker.class, Mockito.CALLS_REAL_METHODS);
        Mockito.when(referenceChecker.refType()).thenReturn("referenceType");
        assertEquals("A value of referenceType", referenceChecker.buildImportDataExempleForheader());
        final GroovyExpressionChecker groovyExpressionChecker = Mockito.mock(GroovyExpressionChecker.class, Mockito.CALLS_REAL_METHODS);
        assertEquals("a string", groovyExpressionChecker.buildImportDataExempleForheader());
        final StringChecker stringChecker = Mockito.mock(StringChecker.class, Mockito.CALLS_REAL_METHODS);
        assertEquals("a string", stringChecker.buildImportDataExempleForheader());
    }

    @Test
    void testBooleanCheckerBuildFieldtype() {
        BooleanChecker checker = new BooleanChecker(CheckerDescription.CheckerDescriptionType.BooleanChecker, Multiplicity.ONE, true, true);
        Object fieldType = checker.buildFieldtype(repository, publishContextBuilder, target, transformer);
        assertNotNull(fieldType);
        assertEquals("fr.inra.oresing.domain.checker.type.BooleanType", fieldType.getClass().getName());
    }

    @Test
    void testReferenceValueDecorator() {
        DataValue dataValue = Mockito.mock(DataValue.class, "dataValue");
        Mockito.when(dataValue.getHierarchicalKey()).thenReturn(Ltree.fromSql("hierarchical.key"));
        Mockito.when(dataValue.getNaturalKey()).thenReturn(Ltree.fromSql("natural_key"));
        DataDatum refValues = Mockito.mock(DataDatum.class, "refValues");
        Mockito.when(dataValue.getRefValues()).thenReturn(refValues);
        ImmutableMap<String, Object> values = ImmutableMap.of();
        Mockito.when(refValues.toObjectsExposedInGroovyContext()).thenReturn(values);
        final CheckerDescription.ReferenceValueDecorator referenceValueDecorator = new CheckerDescription.ReferenceValueDecorator(dataValue);
        Assertions.assertThat(referenceValueDecorator)
                .hasFieldOrPropertyWithValue("hierarchicalKey", "hierarchical.key")
                .hasFieldOrPropertyWithValue("naturalKey", "natural_key")
                .hasFieldOrPropertyWithValue("refValues",  values);
    }

    @Test
    void testDateCheckerBuildFieldtype() {
        DateChecker checker = new DateChecker(CheckerDescription.CheckerDescriptionType.DateChecker, Multiplicity.ONE, true, "yyyy-MM-dd", null, null, null);
        Object fieldType = checker.buildFieldtype(repository, publishContextBuilder, target, transformer);
        assertNotNull(fieldType);
        assertEquals("fr.inra.oresing.domain.checker.type.DateType", fieldType.getClass().getName());
        // On peut aussi vérifier le pattern si besoin
        assertEquals(DateTimeFormatter.ofPattern("yyyy-MM-dd").toString(),
                ((DateType) fieldType).formatter.toString());
    }

    @Test
    void testFloatCheckerBuildFieldtype() {
        FloatChecker checker = new FloatChecker(CheckerDescription.CheckerDescriptionType.FloatChecker, Multiplicity.ONE, true, 1.0f, 10.0f);
        Object fieldType = checker.buildFieldtype(repository, publishContextBuilder, target, transformer);
        assertNotNull(fieldType);
        assertEquals("fr.inra.oresing.domain.checker.type.FloatType", fieldType.getClass().getName());
    }

    @Test
    void testIntegerCheckerBuildFieldtype() {
        IntegerChecker checker = new IntegerChecker(CheckerDescription.CheckerDescriptionType.IntegerChecker, Multiplicity.ONE, true, 1, 100);
        Object fieldType = checker.buildFieldtype(repository, publishContextBuilder, target, transformer);
        assertNotNull(fieldType);
        assertEquals("fr.inra.oresing.domain.checker.type.IntegerType", fieldType.getClass().getName());
    }

    @Test
    void testStringCheckerBuildFieldtype() {
        StringChecker checker = new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker, Multiplicity.ONE, true, ".*");
        StringType fieldType = checker.buildFieldtype(repository, publishContextBuilder, target, transformer);
        assertNotNull(fieldType);
        assertEquals("",fieldType.getValue());
        assertEquals("fr.inra.oresing.domain.checker.type.StringType", fieldType.getClass().getName());
    }

    @Test
    void testComputationCheckerBuildFieldtype() {
        ComputationChecker checker = new ComputationChecker(CheckerDescription.CheckerDescriptionType.StringChecker, Multiplicity.ONE, true, "expression", Set.of(), Set.of());
        FieldType<?> fieldType = checker.buildFieldtype(repository, publishContextBuilder, target, transformer);
        assertNotNull(fieldType);
        assertEquals("",fieldType.getValue());
        assertEquals("fr.inra.oresing.domain.checker.type.StringType", fieldType.getClass().getName());
    }

    @Test
    void testReferenceCheckerBuildFieldtype() {
        ReferenceChecker checker = Mockito.mock(ReferenceChecker.class, Mockito.CALLS_REAL_METHODS);
        Mockito.when(checker.type()).thenReturn(CheckerDescription.CheckerDescriptionType.ReferenceChecker);
        Mockito.when(checker.multiplicity()).thenReturn(Multiplicity.ONE);
        Mockito.when(checker.required()).thenReturn(true);
        Mockito.when(checker.refType()).thenReturn("refType");
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        Mockito.when(repository.getDataIdPerKeys("refType")).thenReturn(ImmutableMap.of(
                new DataValue.LineIdentityPatternColumnName(
                        new DataValue.LineIdentityColumnName(Ltree.fromSql("path1"), Ltree.fromSql("path1")),
                        "data1"
                ),
                uuid1,
                new DataValue.LineIdentityPatternColumnName(
                        new DataValue.LineIdentityColumnName(Ltree.fromSql("path2"), Ltree.fromSql("path2")),
                        "data2"
                ),
                uuid2
        ));
        // Mock repository.getDataIdPerKeys() et autres méthodes nécessaires ici

        Object fieldType = checker.buildFieldtype(repository, publishContextBuilder, target, transformer);
        assertNotNull(fieldType);
        assertEquals("fr.inra.oresing.domain.checker.type.ReferenceType", fieldType.getClass().getName());
    }

    @Test
    void testGroovyExpressionCheckerBuildFieldtype() {
        GroovyExpressionChecker checker = new GroovyExpressionChecker(
                CheckerDescription.CheckerDescriptionType.GroovyExpressionChecker,
                Multiplicity.ONE,
                true,
                "expression",
                Set.of(),
                Set.of()
        );
        Object fieldType = checker.buildFieldtype(repository, publishContextBuilder, target, transformer);
        assertNotNull(fieldType);
        assertEquals("fr.inra.oresing.domain.checker.type.BooleanType", fieldType.getClass().getName());
    }
}