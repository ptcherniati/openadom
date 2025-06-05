package fr.inra.oresing.rest.model.authorization;

import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.domain.data.AuthorizationColumnsDescription;
import fr.inra.oresing.domain.data.menu.ReferenceScope.Context;
import fr.inra.oresing.domain.internationalization.Internationalization;
import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.*;
import java.util.stream.Collectors;


public record GetGrantableResult(
        ImmutableSortedSet<ApplicationUserResult> users,
        Map<String, List<ReferenceScope>> referenceScopes,
        Map<String, SortedMap<String, ColumnDescription>> columnsDescription,
        AuthorizationsResult authorizationsForUser,
        Map<String, List<AuthorizationForScope>> publicAuthorizations) {
    public static final Map<String, AuthorizationColumnsDescription> COLUMNS_DESCRIPTION = Arrays.stream(OperationType.values())
            .collect(Collectors.toMap(OperationType::toString, OperationType::getAuthorizationColumnsDescription));


    public record User(UUID id, String label) {
    }


    public record ColumnDescription(boolean display, String title, boolean withPeriods, boolean withDataGroups,
                                    boolean forPublic, boolean forRequest,
                                    Internationalization internationalizationName) {
    }


    public record ReferenceScope(String id, String datatype, Map<String, String> i18n, Set<Option> nodes) {

        public ReferenceScope(Context context, List<fr.inra.oresing.domain.data.menu.ReferenceScope.TreeNode> nodes) {
            this(
                    context.reference(),
                    context.datatype(),
                    Map.of(
                            "fr", context.exportHeader_fr(),
                            "en", context.exportHeader_en()
                    ),
                    nodes.stream().map(Option::new).collect(Collectors.toSet()));
        }

        public record Option(
                String id,
                String type,
                String naturalKey,
                Map<String, String> i18n,
                Set<Option> children
        ) {
            public Option(fr.inra.oresing.domain.data.menu.ReferenceScope.TreeNode node) {
                this(
                        node.node().node().node_key().getSql(),
                        node.node().node_type(),
                        node.node().node_nk().getSql(),
                        Map.of(
                                "fr", node.node().node().fr() != null ? node.node().node().fr() : node.node().node().value(),
                                "en", node.node().node().en() != null ? node.node().node().en() : node.node().node().value()
                        ),
                        node.children().stream().map(Option::new).collect(Collectors.toSet()));
            }
        }
    }
}