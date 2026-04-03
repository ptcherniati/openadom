package fr.inra.oresing.domain.application.configuration;

import java.util.List;

public class AuthorizationBuilder {

    public static AuthorizationScopeComponentDataBuilder authorizationScopeComponentData() {
        return new AuthorizationScopeComponentDataBuilder();
    }

    public static AuthorizationMainBuilder authorization() {
        return new AuthorizationMainBuilder();
    }

    public static class AuthorizationScopeComponentDataBuilder {
        private String component;
        private String data;

        public AuthorizationScopeComponentDataBuilder component(String component) {
            this.component = component;
            return this;
        }

        public AuthorizationScopeComponentDataBuilder data(String data) {
            this.data = data;
            return this;
        }

        public AuthorizationScopeComponentData build() {
            return new AuthorizationScopeComponentData(component, data);
        }
    }

    public static class AuthorizationMainBuilder {
        private List<AuthorizationScopeComponentData> authorizationScope = List.of();
        private String timeScope;

        public AuthorizationMainBuilder authorizationScope(List<AuthorizationScopeComponentData> authorizationScope) {
            this.authorizationScope = authorizationScope;
            return this;
        }

        public AuthorizationMainBuilder timeScope(String timeScope) {
            this.timeScope = timeScope;
            return this;
        }

        public Authorization build() {
            return new Authorization(authorizationScope, timeScope);
        }
    }
}