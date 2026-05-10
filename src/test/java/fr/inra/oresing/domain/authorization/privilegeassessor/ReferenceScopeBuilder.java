package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.authorization.GetGrantableResult;

import java.util.LinkedList;
import java.util.List;

public class ReferenceScopeBuilder {
    MenuType menuType = MenuType.authorization;
    List<ReferenceScope.TreeNode> nodes = new LinkedList<>();

    public static final ReferenceScopeBuilder builder() {
        return new ReferenceScopeBuilder();
    }

    public GetGrantableResult.ReferenceScope build() {
        return new GetGrantableResult.ReferenceScope(
                new ReferenceScope.Context(
                        "datatype",
                        "reference",
                        menuType,
                        "header_fr",
                        "header_en"),
                nodes
        );
    }

    public ReferenceScopeBuilder withSubmission() {
        this.menuType = MenuType.submission;
        return this;
    }

    public ReferenceScopeBuilder withMenuType(MenuType menuType) {
        this.menuType = menuType;
        return this;
    }

    public ReferenceScopeBuilder withNode(ReferenceScope.TreeNode node) {
        this.nodes.add(node);
        return this;
    }
}