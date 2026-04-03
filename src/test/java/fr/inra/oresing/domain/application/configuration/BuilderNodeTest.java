package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Tag("core.config")
@ExtendWith(MockitoExtension.class)
class BuilderNodeTest {

    @Test
    void withAllDepends_shouldAddTransitiveDependencies() {
        // Arrange
        // Créer un nœud A qui dépend de B
        BuilderNode nodeA = new BuilderNode(0, "A", null, null, null, Set.of(), Set.of("B"), null, false);

        // Créer un nœud B qui dépend de C
        WithDepends nodeB = mock(WithDepends.class);
        when(nodeB.nodeName()).thenReturn("B");
        when(nodeB.depends()).thenReturn(Set.of("C"));

        // Créer un nœud C qui ne dépend de rien
        WithDepends nodeC = mock(WithDepends.class);
        when(nodeC.nodeName()).thenReturn("C");
        when(nodeC.depends()).thenReturn(Set.of());

        Collection<WithDepends> allNodes = List.of(nodeB, nodeC);

        // Act
        BuilderNode result = nodeA.withAllDepends(allNodes);

        // Assert
        assertEquals(2, result.level()); // Le niveau devrait être 1 car il y a une dépendance transitive
        assertTrue(result.depends().contains("B")); // Dépendance directe
        assertTrue(result.depends().contains("C")); // Dépendance transitive
        assertEquals(2, result.depends().size()); // Total des dépendances
    }

    @Test
    void withAllDepends_shouldThrowExceptionOnCyclicDependency() {
        // Arrange
        // Créer un nœud A qui dépend de B
        BuilderNode nodeA = new BuilderNode(0, "A", null, null, null, Set.of(), Set.of("B"), null, false);

        // Créer un nœud B qui dépend de C
        WithDepends nodeB = mock(WithDepends.class);
        when(nodeB.nodeName()).thenReturn("B");
        when(nodeB.depends()).thenReturn(Set.of("C"));

        // Créer un nœud C qui dépend de A (créant un cycle)
        WithDepends nodeC = mock(WithDepends.class);
        when(nodeC.nodeName()).thenReturn("C");
        when(nodeC.depends()).thenReturn(Set.of("A"));

        Collection<WithDepends> allNodes = List.of(nodeB, nodeC);

        // Act & Assert
        assertThrows(BadApplicationConfigurationException.class, () -> {
            nodeA.withAllDepends(allNodes);
        });
    }
}