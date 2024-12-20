package fr.inra.oresing.domain.data.menu;

import com.fasterxml.jackson.annotation.JsonProperty;
import fr.inra.oresing.domain.application.configuration.Ltree;
import org.apache.logging.log4j.util.Strings;

import java.util.*;

public record ReferenceScope(Map<Context, MenuNode> menuNodes) {
    public ReferenceScope() {
        this(new HashMap<>());
    }

    public record Context(
            String datatype,
            String reference,
            MenuType treeType,
            String exportHeader_fr,
            String exportHeader_en

    ) {

    }

    public record NodeDescription(
            Context context,
            @JsonProperty("node_key") Ltree node_key,
            String node_type,
            @JsonProperty("node_nk") Ltree node_nk,
            String parent_type,
            @JsonProperty("parent_nk") Ltree parent_nk,
            Node node
    ) {
        public boolean isRoot() {
            return Strings.isEmpty(parent_type());
        }
    }

    public record Node(
            String fr,
            String en,
            String value,
            Ltree node_NK,
            Ltree node_key,
            String nodetype

    ) {
    }

    public record MenuNode(
            String dataname,
            MenuType treeType,
            String exportHeader_fr,
            String exportHeader_en,
            NodeDescription node
    ) {

    }

    public record TreeNode(Ltree key, NodeDescription node, List<TreeNode> children) {
        public boolean containsContextNode() {
            if (node().context.reference().equals(node().node_type)) {
                return true;
            }
            if (children().isEmpty()) {
                return false;
            }
            return children().stream().anyMatch(TreeNode::containsContextNode);
        }
    }
}
