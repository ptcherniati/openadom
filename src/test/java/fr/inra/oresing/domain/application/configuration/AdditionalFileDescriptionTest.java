package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

@Tag("core.config")
class AdditionalFileDescriptionTest {

    @Test
    void testAdditionalFileDescriptionBuilder() {
        Map<String, FieldDescription> formFields = Map.of("field1", FieldDescriptionBuilder.additionalFileField().build());
        AdditionalFileDescription description = new AdditionalFileDescriptionBuilder()
                .formFields(formFields)
                .build();

        Assertions.assertEquals(formFields, description.formFields());
    }
}