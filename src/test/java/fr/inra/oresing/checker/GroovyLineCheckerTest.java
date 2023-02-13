package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.groovy.GroovyExpression;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.transformer.TransformationConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class GroovyLineCheckerTest {

    private static final ImmutableMap<String, Object> EMPTY_CONTEXT = ImmutableMap.of();

    @Test
    public void testChecker() {
        String expression = String.join("\n"
                , "Integer température = Integer.parseInt(datum.get(\"temperature\").get(\"valeur\"));"
                , "String unité = datum.get(\"temperature\").get(\"unité\");"
                , "if (\"°C\".equals(unité)) {"
                , "    return température >= -273.15;"
                , "} else if (\"kelvin\".equals(unité)) {"
                , "    return température >= 0;"
                , "}"
                , "throw new IllegalArgumentException(\"unité inconnue, \" + unité);"
        );
        String wrongExpression = expression.replace("Integer", "Integre");

        Assert.assertFalse(GroovyLineChecker.validateExpression(expression).isPresent());
        Optional<GroovyExpression.CompilationError> compilationErrorOptional = GroovyLineChecker.validateExpression(wrongExpression);
        Assert.assertTrue(compilationErrorOptional.isPresent());
        compilationErrorOptional.ifPresent(compilationError -> {
            Assert.assertTrue(compilationError.getMessage().contains("Integre"));
        });

        GroovyLineChecker groovyLineChecker = GroovyLineChecker.forExpression(expression, EMPTY_CONTEXT, getConfiguration(expression));
        ImmutableMap<VariableComponentKey, String> validDatum =
                ImmutableMap.of(
                        new VariableComponentKey("temperature", "valeur"), "-12",
                        new VariableComponentKey("temperature", "unité"), "°C"
                );
        ImmutableMap<VariableComponentKey, String> invalidDatum =
                ImmutableMap.of(
                        new VariableComponentKey("temperature", "valeur"), "-12",
                        new VariableComponentKey("temperature", "unité"), "kelvin"
                );
        ImmutableMap<VariableComponentKey, String> invalidDatum2 =
                ImmutableMap.of(
                        new VariableComponentKey("temperature", "valeur"), "-12",
                        new VariableComponentKey("temperature", "unité"), "degrés"
                );

        Assert.assertTrue(groovyLineChecker.check(new Datum(validDatum)).isSuccess());
        Assert.assertFalse(groovyLineChecker.check(new Datum(invalidDatum)).isSuccess());
        try {
            groovyLineChecker.check(new Datum(invalidDatum2)).isSuccess();
            Assert.fail("une exception aurait dû être levée");
        } catch (SiOreIllegalArgumentException e) {
            Assert.assertEquals("badGroovyExpressionChecker", e.getMessage());
            Assert.assertEquals("Integer température = Integer.parseInt(datum.get(\"temperature\").get(\"valeur\"));\n" +
                    "String unité = datum.get(\"temperature\").get(\"unité\");\n" +
                    "if (\"°C\".equals(unité)) {\n" +
                    "    return température >= -273.15;\n" +
                    "} else if (\"kelvin\".equals(unité)) {\n" +
                    "    return température >= 0;\n" +
                    "}\n" +
                    "throw new IllegalArgumentException(\"unité inconnue, \" + unité);", e.getParams().get("expression"));
            Assert.assertEquals("java.lang.IllegalArgumentException: unité inconnue, degrés", e.getParams().get("message"));
        }
    }

    @Test
    public void testCheckerWithNonBooleanValue() {
        String expression = String.join("\n"
                , "Integer température = Integer.parseInt(datum.get(\"temperature\").get(\"valeur\"));"
                , "String unité = datum.get(\"temperature\").get(\"unité\");"
                , "if (\"°C\".equals(unité)) {"
                , "    return température +273.15;"
                , "} else if (\"kelvin\".equals(unité)) {"
                , "    return température;"
                , "}"
                , "throw new IllegalArgumentException(\"unité inconnue, \" + unité);"
        );
        GroovyLineChecker groovyLineChecker = GroovyLineChecker.forExpression(expression, EMPTY_CONTEXT, getConfiguration(expression));
        ImmutableMap<VariableComponentKey, String> validDatum =
                ImmutableMap.of(
                        new VariableComponentKey("temperature", "valeur"), "-12",
                        new VariableComponentKey("temperature", "unité"), "°C"
                );
        try {
            ValidationCheckResult validation = groovyLineChecker.check(new Datum(validDatum));
            Assert.fail("une exception aurait dû être levée");
        } catch (SiOreIllegalArgumentException e) {
            Assert.assertEquals("badGroovyExpressionCheckerReturnType", e.getMessage());
            Assert.assertEquals("261.15", e.getParams().get("value").toString());
            Assert.assertEquals(Set.of(CheckerReturnType.BOOLEAN), e.getParams().get("knownCheckerReturnType"));
        }
    }

    private GroovyLineCheckerConfiguration getConfiguration(String expression) {
        return new GroovyLineCheckerConfiguration() {
            @Override
            public TransformationConfiguration getTransformation() {
                throw new UnsupportedOperationException("doublure de test");
            }

            @Override
            public boolean isRequired() {
                throw new UnsupportedOperationException("doublure de test");
            }

            @Override
            public GroovyConfiguration getGroovy() {
                return new GroovyConfiguration() {
                    @Override
                    public String getExpression() {
                        return expression;
                    }

                    @Override
                    public Set<String> getReferences() {
                        return Collections.emptySet();
                    }

                    @Override
                    public Set<String> getDatatypes() {
                        return Collections.emptySet();
                    }
                };
            }
        };
    }
}