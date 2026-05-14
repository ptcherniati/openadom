package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Tag("core.config")
@Tag("domain.model")
class ValidationTest {

    @Test
    void testBuildError() {
        AtomicReference<ValidationParams> validationParams = new AtomicReference<>();
        Validation validation = new Validation(validationParams::set, "path", Map.of());

        validation.buildError(ConfigurationException.BAD_VERSION_PATTERN);

        Assertions.assertEquals(ConfigurationException.BAD_VERSION_PATTERN, validationParams.get().exception());
        Assertions.assertEquals("path", validationParams.get().path());
        Assertions.assertEquals(Map.of(), validationParams.get().params());
    }

    @Test
    void testBuildErrorWithParams() {
        AtomicReference<ValidationParams> validationParams = new AtomicReference<>();
        Validation validation = new Validation(validationParams::set, "path", Map.of());

        Map<String, Object> params = Map.of("param1", "value1");
        validation.buildError(ConfigurationException.BAD_VERSION_PATTERN, params);

        Assertions.assertEquals(ConfigurationException.BAD_VERSION_PATTERN, validationParams.get().exception());
        Assertions.assertEquals("path", validationParams.get().path());
        Assertions.assertEquals(params, validationParams.get().params());
    }
}