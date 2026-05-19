package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescriptionBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
class FieldDescriptionTest {

    @Test
    void testRightsRequestFieldBuilder() {
        CheckerDescription checker = CheckerDescriptionBuilder.integerChecker().build();
        RightsRequestField field = FieldDescriptionBuilder.rightsRequestField()
                .order(2)
                .required(true)
                .checker(checker)
                .build();

        Assertions.assertEquals(2, field.order());
        Assertions.assertTrue(field.required());
        Assertions.assertEquals(checker, field.checker());
        Assertions.assertEquals(FieldDescription.FieldDescriptionType.RightsRequestField, field.type());
    }

    @Test
    void testAdditionalFileFieldBuilder() {
        CheckerDescription checker = CheckerDescriptionBuilder.integerChecker().build();
        AdditionalFileField field = FieldDescriptionBuilder.additionalFileField()
                .order(3)
                .required(false)
                .checker(checker)
                .build();

        Assertions.assertEquals(3, field.order());
        Assertions.assertFalse(field.required());
        Assertions.assertEquals(checker, field.checker());
        Assertions.assertEquals(FieldDescription.FieldDescriptionType.AdditionalFileField, field.type());
    }
}