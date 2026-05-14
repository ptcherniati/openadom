package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

@Tag("core.config")
@Tag("domain.model")
class RightRequestDescriptionTest {

    @Test
    void testRightRequestDescriptionBuilder() {
        Map<String, FieldDescription> formFields = Map.of("field1", FieldDescriptionBuilder.rightsRequestField().build());
        RightRequestDescription description = new RightRequestDescriptionBuilder()
                .formFields(formFields)
                .build();

        Assertions.assertEquals(formFields, description.formFields());
    }
}