package fr.inra.oresing.domain.data.deposit.context.hierarchicalkey;
import fr.inra.oresing.domain.application.configuration.HierarchicalNode;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.Node;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnSingleValue;
import fr.inra.oresing.domain.data.DataDatum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.TreeSet;
import static org.assertj.core.api.Assertions.assertThat;
@Tag("domain.model")
@DisplayName("HierarchicalKeyFactory - implementations non composites et root")
class HierarchicalKeyFactoryTest {

    private static Node buildNode(String componentKey, String parent, boolean recursive) {
        return new Node(1, componentKey, componentKey, null, parent, new TreeSet<>(), Set.of(), 0, recursive);
    }

    // ---- ForNotCompositeReference ----
    @Test
    @DisplayName("ForNotCompositeReference.newHierarchicalKey retourne la cle naturelle")
    void forNotComposite_newHierarchicalKey_returnsNaturalKey() {
        ForNotCompositeReference factory = new ForNotCompositeReference("myRef");
        Ltree naturalKey = Ltree.fromSql("key1");
        DataDatum datum = new DataDatum();
        Ltree result = factory.newHierarchicalKey(naturalKey, datum);
        assertThat(result).isEqualTo(naturalKey);
    }
    @Test
    @DisplayName("ForNotCompositeReference.newHierarchicalReference retourne la reference")
    void forNotComposite_newHierarchicalReference_returnsReference() {
        ForNotCompositeReference factory = new ForNotCompositeReference("myRef");
        Ltree ref = Ltree.fromSql("ref1");
        Ltree result = factory.newHierarchicalReference(ref);
        assertThat(result).isEqualTo(ref);
    }
    @Test
    @DisplayName("ForNotCompositeReference.node retourne null")
    void forNotComposite_node_returnsNull() {
        ForNotCompositeReference factory = new ForNotCompositeReference("myRef");
        assertThat(factory.node()).isNull();
    }
    @Test
    @DisplayName("ForNotCompositeReference.parent retourne une chaine vide (par defaut)")
    void forNotComposite_parent_returnsEmpty() {
        ForNotCompositeReference factory = new ForNotCompositeReference("myRef");
        assertThat(factory.parent()).isEmpty();
    }
    @Test
    @DisplayName("ForNotCompositeReference.refType retourne le refType")
    void forNotComposite_refType() {
        ForNotCompositeReference factory = new ForNotCompositeReference("myDataType");
        assertThat(factory.refType()).isEqualTo("myDataType");
    }
    @Test
    @DisplayName("ForNotCompositeReference - equals / hashCode")
    void forNotComposite_equalsHashCode() {
        ForNotCompositeReference f1 = new ForNotCompositeReference("ref");
        ForNotCompositeReference f2 = new ForNotCompositeReference("ref");
        assertThat(f1).isEqualTo(f2);
        assertThat(f1.hashCode()).isEqualTo(f2.hashCode());
    }

    // ---- ForCompositeReferenceRoot ----

    @Nested
    @DisplayName("ForCompositeReferenceRoot")
    class ForCompositeReferenceRootTest {

        @Test
        @DisplayName("newHierarchicalKey() retourne la clé naturelle telle quelle")
        void newHierarchicalKeyIsNaturalKey() {
            Node node = buildNode("root", null, false);
            HierarchicalNode hierarchicalNode = new HierarchicalNode(node);
            ForCompositeReferenceRoot factory = new ForCompositeReferenceRoot(hierarchicalNode, "myref");
            Ltree naturalKey = Ltree.fromSql("a.b");
            DataDatum datum = new DataDatum();
            Ltree result = factory.newHierarchicalKey(naturalKey, datum);
            assertThat(result).isEqualTo(naturalKey);
        }

        @Test
        @DisplayName("newHierarchicalReference() retourne la référence telle quelle")
        void newHierarchicalReferenceIsReference() {
            Node node = buildNode("root", null, false);
            HierarchicalNode hierarchicalNode = new HierarchicalNode(node);
            ForCompositeReferenceRoot factory = new ForCompositeReferenceRoot(hierarchicalNode, "myref");
            Ltree reference = Ltree.fromSql("x.y.z");
            Ltree result = factory.newHierarchicalReference(reference);
            assertThat(result).isEqualTo(reference);
        }

        @Test
        @DisplayName("record accessors")
        void recordAccessors() {
            Node node = buildNode("k", null, false);
            HierarchicalNode hn = new HierarchicalNode(node);
            ForCompositeReferenceRoot factory = new ForCompositeReferenceRoot(hn, "ref1");
            assertThat(factory.node()).isSameAs(hn);
            assertThat(factory.refType()).isEqualTo("ref1");
        }
    }

    // ---- ForCompositeReferenceChild ----

    @Nested
    @DisplayName("ForCompositeReferenceChild")
    class ForCompositeReferenceChildTest {

        @Test
        @DisplayName("newHierarchicalKey() sans parent dans le datum retourne la clé naturelle")
        void newHierarchicalKeyWithoutParent() {
            Node node = buildNode("child", "parent", false);
            HierarchicalNode hierarchicalNode = new HierarchicalNode(node);
            ForCompositeReferenceChild factory = new ForCompositeReferenceChild(hierarchicalNode, "child_ref");
            Ltree naturalKey = Ltree.fromSql("leaf");
            DataDatum datum = new DataDatum();
            Ltree result = factory.newHierarchicalKey(naturalKey, datum);
            assertThat(result).isEqualTo(naturalKey);
        }

        @Test
        @DisplayName("newHierarchicalKey() avec parent vide dans le datum retourne la clé naturelle")
        void newHierarchicalKeyWithEmptyParent() {
            Node node = buildNode("child", "parent_col", false);
            HierarchicalNode hierarchicalNode = new HierarchicalNode(node);
            ForCompositeReferenceChild factory = new ForCompositeReferenceChild(hierarchicalNode, "child_ref");
            Ltree naturalKey = Ltree.fromSql("leaf");
            DataDatum datum = new DataDatum();
            datum.put(new DataColumn("child"), new DataColumnSingleValue(
                    StringType.getStringTypeFromStringValue("")));
            Ltree result = factory.newHierarchicalKey(naturalKey, datum);
            assertThat(result).isEqualTo(naturalKey);
        }

        @Test
        @DisplayName("newHierarchicalReference() préfixe la référence avec le parent")
        void newHierarchicalReferencePrexixesWithParent() {
            Node node = buildNode("child", "parent_node", false);
            HierarchicalNode hierarchicalNode = new HierarchicalNode(node);
            ForCompositeReferenceChild factory = new ForCompositeReferenceChild(hierarchicalNode, "child_ref");
            Ltree reference = Ltree.fromSql("leaf");
            Ltree result = factory.newHierarchicalReference(reference);
            assertThat(result.getSql()).contains("leaf");
        }

        @Test
        @DisplayName("record accessors")
        void recordAccessors() {
            Node node = buildNode("c", "p", false);
            HierarchicalNode hn = new HierarchicalNode(node);
            ForCompositeReferenceChild factory = new ForCompositeReferenceChild(hn, "child");
            assertThat(factory.node()).isSameAs(hn);
            assertThat(factory.refType()).isEqualTo("child");
        }
    }
}
