package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

@Tag("core.config")
@Tag("domain.model")
class AuthorizationTest {

    @Test
    void testAuthorizationScopeComponentDataBuilder() {
        AuthorizationScopeComponentData data = AuthorizationBuilder.authorizationScopeComponentData()
                .component("comp")
                .data("data")
                .build();

        Assertions.assertEquals("comp", data.component());
        Assertions.assertEquals("data", data.data());
    }

    @Test
    void testAuthorizationBuilder() {
        AuthorizationScopeComponentData data = AuthorizationBuilder.authorizationScopeComponentData().build();
        List<AuthorizationScopeComponentData> dataList = List.of(data);

        Authorization auth = AuthorizationBuilder.authorization()
                .authorizationScope(dataList)
                .timeScope("timeScope")
                .build();

        Assertions.assertEquals(dataList, auth.authorizationScope());
        Assertions.assertEquals("timeScope", auth.timeScope());
    }
}