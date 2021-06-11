package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.rest.ValidationCheckResult;
import jdk.jshell.JShell;
import jdk.jshell.SnippetEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

@Slf4j
public class GroovyLineCheckerTest {

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
        Optional<GroovyLineChecker.CompilationError> compilationErrorOptional = GroovyLineChecker.validateExpression(wrongExpression);
        Assert.assertTrue(compilationErrorOptional.isPresent());
        compilationErrorOptional.ifPresent(compilationError -> {
            Assert.assertTrue(compilationError.getMessage().contains("Integre"));
        });

        GroovyLineChecker groovyLineChecker = GroovyLineChecker.forExpression(expression);
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

        Assert.assertTrue(groovyLineChecker.check(validDatum).isSuccess());
        Assert.assertFalse(groovyLineChecker.check(invalidDatum).isSuccess());
        try {
            groovyLineChecker.check(invalidDatum2).isSuccess();
            Assert.fail("une exception aurait dû être levée");
        } catch (OreSiTechnicalException e) {
            Assert.assertTrue(e.getCause().getMessage().contains("IllegalArgumentException: unité inconnue, degrés"));
            if (log.isDebugEnabled()) {
                log.debug("erreur si le script lève une exception", e);
            }
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

        GroovyLineChecker groovyLineChecker = GroovyLineChecker.forExpression(expression);
        ImmutableMap<VariableComponentKey, String> validDatum =
                ImmutableMap.of(
                        new VariableComponentKey("temperature", "valeur"), "-12",
                        new VariableComponentKey("temperature", "unité"), "°C"
                );
        try {
            ValidationCheckResult validation = groovyLineChecker.check(validDatum);
            Assert.fail("une exception aurait dû être levée");
        } catch (OreSiTechnicalException e) {
            Assert.assertTrue(e.getMessage().contains("L'évaluation de l’expression n'a pas retourné une valeur booléenne mais 261.15."));
        }
    }

    @Test
    @Ignore("juste un essai")
    public void testJShell() {
        try (JShell jShell = JShell.create()) {
            String uneVariable = "truc";
            List<SnippetEvent> events = jShell.eval("uneVariable");
            for (SnippetEvent event : events) {
                //System.out.println(event.value());
            }
        }
    }
}