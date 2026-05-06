package fr.inra.oresing.domain.data.deposit.context.hierarchicalkey;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataDatum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
@Tag("domain.model")
@DisplayName("HierarchicalKeyFactory - implementations non composites et root")
class HierarchicalKeyFactoryTest {
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
}
