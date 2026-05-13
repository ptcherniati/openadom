package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

@Tag("core.config")
@Tag("domain.model")
class DateCheckerTypeTest {

    @Test
    void testEmptyInstance() {
        DateCheckerType dateCheckerType = DateCheckerType.EMPTY_INSTANCE();
        Assertions.assertEquals(CheckerEnum.OA_date, dateCheckerType.getChecker());
        Assertions.assertTrue(dateCheckerType.children().containsKey(ConfigurationSchemaNode.OA_NAME));
        Assertions.assertTrue(dateCheckerType.children().containsKey(ConfigurationSchemaNode.OA_PARAMS));
        Assertions.assertInstanceOf(EnumType.class, dateCheckerType.children().get(ConfigurationSchemaNode.OA_NAME));
    }

    @Test
    void testConstructor() {
        Map<String, ConfigurationSchemaNodeType<?>> children = Map.of(
                ConfigurationSchemaNode.OA_PARAMS, DateCheckerParamsType.EMPTY_INSTANCE()
        );
        DateCheckerType dateCheckerType = new DateCheckerType(children);
        Assertions.assertEquals(CheckerEnum.OA_date, dateCheckerType.getChecker());
        Assertions.assertInstanceOf(EnumType.class, dateCheckerType.children().get(ConfigurationSchemaNode.OA_NAME));
    }
}