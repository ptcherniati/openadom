package fr.inra.oresing.domain.repository.authorization;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires du normaliseur de hiérarchie d'opérations
 * ( ticket #521 - réponse Damien Maurice 2026-05-25 ) .
 *
 * <p>Couvre :
 * <ul>
 *   <li>Hiérarchie stricte ( delete &gt; depot/publication &gt; extraction )</li>
 *   <li>Miroir auto {@code depot &lt;-&gt; publication}</li>
 *   <li>Auto-correction silencieuse des payloads partiels ( cas client API )</li>
 *   <li>Edge cases : null , vide , doublons , éléments null dans le set</li>
 *   <li>Non-modification de l'entrée ( immuabilité défensive )</li>
 * </ul>
 */
@Tag("domain.model")
@DisplayName("OperationTypeHierarchy.normalize")
class OperationTypeHierarchyTest {

    @Test
    @DisplayName("null en entrée → set vide")
    void nullInput() {
        assertThat(OperationTypeHierarchy.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("set vide en entrée → set vide")
    void emptyInput() {
        assertThat(OperationTypeHierarchy.normalize(EnumSet.noneOf(OperationType.class))).isEmpty();
    }

    @Test
    @DisplayName("[extraction] reste [extraction]")
    void extractionOnlyStaysExtractionOnly() {
        Set<OperationType> result = OperationTypeHierarchy.normalize(EnumSet.of(OperationType.extraction));
        assertThat(result).containsExactly(OperationType.extraction);
    }

    @Test
    @DisplayName("[depot] → [depot, publication, extraction]")
    void depotAddsPublicationAndExtraction() {
        Set<OperationType> result = OperationTypeHierarchy.normalize(EnumSet.of(OperationType.depot));
        assertThat(result).containsExactlyInAnyOrder(
                OperationType.depot,
                OperationType.publication,
                OperationType.extraction
        );
    }

    @Test
    @DisplayName("[publication] → [depot, publication, extraction] ( miroir auto )")
    void publicationMirrorsDepot() {
        Set<OperationType> result = OperationTypeHierarchy.normalize(EnumSet.of(OperationType.publication));
        assertThat(result).containsExactlyInAnyOrder(
                OperationType.depot,
                OperationType.publication,
                OperationType.extraction
        );
    }

    @Test
    @DisplayName("[delete] → [delete, depot, publication, extraction]")
    void deleteImpliesAll() {
        Set<OperationType> result = OperationTypeHierarchy.normalize(EnumSet.of(OperationType.delete));
        assertThat(result).containsExactlyInAnyOrder(
                OperationType.delete,
                OperationType.depot,
                OperationType.publication,
                OperationType.extraction
        );
    }

    @Test
    @DisplayName("[delete, depot, extraction] reste idempotent")
    void fullSetIsIdempotent() {
        Set<OperationType> input = EnumSet.of(
                OperationType.delete,
                OperationType.depot,
                OperationType.publication,
                OperationType.extraction
        );
        Set<OperationType> result = OperationTypeHierarchy.normalize(input);
        assertThat(result).containsExactlyInAnyOrderElementsOf(input);
    }

    @Test
    @DisplayName("[associate] reste [associate] ( pas dans la hiérarchie )")
    void associateUnaffected() {
        Set<OperationType> result = OperationTypeHierarchy.normalize(EnumSet.of(OperationType.associate));
        assertThat(result).containsExactly(OperationType.associate);
    }

    @Test
    @DisplayName("[delete, associate] → tous + associate")
    void deleteWithAssociate() {
        Set<OperationType> result = OperationTypeHierarchy.normalize(
                EnumSet.of(OperationType.delete, OperationType.associate)
        );
        assertThat(result).containsExactlyInAnyOrder(
                OperationType.delete,
                OperationType.depot,
                OperationType.publication,
                OperationType.extraction,
                OperationType.associate
        );
    }

    @Test
    @DisplayName("Ticket #521 Q3 : payload partiel [depot] sans extraction → auto-complété")
    void partialPayloadAutoCompletes() {
        // Cas client API externe ( curl / postman ) qui envoie [depot]
        // sans extraction : on auto-corrige silencieusement vers le haut .
        Set<OperationType> result = OperationTypeHierarchy.normalize(EnumSet.of(OperationType.depot));
        assertThat(result).contains(OperationType.extraction);
    }

    @Test
    @DisplayName("Set non-EnumSet ( HashSet ) avec null → null filtré sans NPE")
    void hashSetWithNullSkipsNull() {
        Set<OperationType> input = new HashSet<>();
        input.add(null);
        input.add(OperationType.extraction);
        Set<OperationType> result = OperationTypeHierarchy.normalize(input);
        assertThat(result).containsExactly(OperationType.extraction);
    }

    @Test
    @DisplayName("L'entrée n'est PAS modifiée par normalize ( immuabilité défensive )")
    void inputNotMutated() {
        Set<OperationType> input = EnumSet.of(OperationType.depot);
        OperationTypeHierarchy.normalize(input);
        assertThat(input).containsExactly(OperationType.depot);
    }
}
