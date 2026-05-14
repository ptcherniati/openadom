package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour DatagroupType.
 */
@Tag("core.config")
@Tag("domain.model")
@DisplayName("DatagroupType – factory methods")
class DatagroupTypeTest {

    @Test
    @DisplayName("EMPTY_INSTANCE() retourne une instance non nulle avec children vide")
    void emptyInstance() {
        DatagroupType instance = DatagroupType.EMPTY_INSTANCE();
        assertThat(instance).isNotNull();
        assertThat(instance.children()).isEmpty();
        assertThat(instance.required()).isFalse();
        assertThat(instance.nullable()).isFalse();
    }

    @Test
    @DisplayName("SECTION_BUILDER() retourne un SectionBuilder non nul")
    void sectionBuilder() {
        SectionBuilder builder = DatagroupType.SECTION_BUILDER();
        assertThat(builder).isNotNull();
    }

    @Test
    @DisplayName("constructeur public avec Map vide crée une instance valide")
    void constructorWithEmptyMap() {
        DatagroupType instance = new DatagroupType(Map.of());
        assertThat(instance).isNotNull();
        assertThat(instance.children()).isEmpty();
        assertThat(instance.sectionBuilder()).isNotNull();
    }

    @Test
    @DisplayName("EMPTY_INSTANCE() et constructeur(Map.of()) ont les mêmes children")
    void emptyInstanceSameAsConstructorEmpty() {
        DatagroupType byFactory = DatagroupType.EMPTY_INSTANCE();
        DatagroupType byConstructor = new DatagroupType(Map.of());
        assertThat(byFactory.children()).isEqualTo(byConstructor.children());
        assertThat(byFactory.required()).isEqualTo(byConstructor.required());
        assertThat(byFactory.nullable()).isEqualTo(byConstructor.nullable());
    }
}