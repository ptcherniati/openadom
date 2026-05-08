package fr.inra.oresing.persistence;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests unitaires pour {@link PgIdentifier} ( fix ACBB index truncation,
 * cf. AUDIT 2026-05-08 + ticket #487 ).
 *
 * <p>Couverture :
 * <ul>
 *   <li>identifiant court : renvoyé tel quel</li>
 *   <li>identifiant à la limite ( budget = identifier.length ) : renvoyé tel quel</li>
 *   <li>identifiant trop long : tronqué + suffixe CRC32 8 chars</li>
 *   <li>déterminisme : 2 appels = même résultat</li>
 *   <li>unicité : 2 datatypes ACBB partageant les 33 premiers chars produisent
 *       des index distincts ( régression du bug d'origine )</li>
 *   <li>contrats : null / reservedSuffixLength négatif / suffixe trop grand</li>
 *   <li>{@link PgIdentifier#wouldBeTruncated} reflète la limite Postgres</li>
 * </ul>
 */
class PgIdentifierTest {

    @Test
    @DisplayName("Identifiant court : renvoyé tel quel")
    void truncateSafe_shortIdentifier_returnedAsIs() {
        final String result = PgIdentifier.truncateSafe("authorization_pem_index", 16);
        assertThat(result).isEqualTo("authorization_pem_index");
    }

    @Test
    @DisplayName("Identifiant pile à la limite ( budget = length ) : renvoyé tel quel")
    void truncateSafe_atBudget_returnedAsIs() {
        // 47 chars, budget = 63 - 16 = 47 → no truncation
        final String name = "a".repeat(47);
        final String result = PgIdentifier.truncateSafe(name, 16);
        assertThat(result).isEqualTo(name);
        assertThat(result).hasSize(47);
    }

    @Test
    @DisplayName("Identifiant trop long : tronqué + suffixe CRC32 8 chars")
    void truncateSafe_overLimit_truncatedWithHash() {
        // 80 chars, budget = 47 → tronque à prefix(38) + '_' + 8 hex = 47
        final String name = "x".repeat(80);
        final String result = PgIdentifier.truncateSafe(name, 16);
        assertThat(result).hasSize(47);
        assertThat(result).startsWith("x".repeat(38));
        assertThat(result.charAt(38)).isEqualTo('_');
        assertThat(result.substring(39)).matches("[0-9a-f]{8}");
    }

    @Test
    @DisplayName("Déterminisme : 2 appels successifs = même résultat")
    void truncateSafe_isDeterministic() {
        final String name = "authorization_t_soil_analysis_sana_complete_long_2026_index";
        assertThat(PgIdentifier.truncateSafe(name, 16))
                .isEqualTo(PgIdentifier.truncateSafe(name, 16));
    }

    @Test
    @DisplayName("Régression bug ACBB : 2 datatypes partageant 33 premiers chars produisent des index distincts")
    void truncateSafe_acbbCollision_isAvoided() {
        // Avant le fix : "authorization_<dataname>_index" tronqué à 63 par
        // Postgres faisait collision si les 33 premiers chars du dataname
        // étaient identiques. Avec PgIdentifier, le hash CRC32 du nom complet
        // garantit l'unicité.
        final String dataname1 = "t_soil_analysis_sana_complete_long_2025_winter";
        final String dataname2 = "t_soil_analysis_sana_complete_long_2025_summer";

        final String index1 = PgIdentifier.truncateSafe(
                "authorization_%s_index".formatted(dataname1), 16);
        final String index2 = PgIdentifier.truncateSafe(
                "authorization_%s_index".formatted(dataname2), 16);

        assertThat(index1).isNotEqualTo(index2);
        assertThat(index1.length() + "_refvalues_index".length()).isLessThanOrEqualTo(63);
        assertThat(index2.length() + "_refvalues_index".length()).isLessThanOrEqualTo(63);
    }

    @Test
    @DisplayName("Unicité globale même quand les 50 premiers chars sont identiques")
    void truncateSafe_longCommonPrefix_remainsUnique() {
        final String common = "authorization_a_very_very_long_common_prefix_used";
        final String a = common + "_alpha_2024_complete_index";
        final String b = common + "_beta_2024_complete_index";

        final String resA = PgIdentifier.truncateSafe(a, 16);
        final String resB = PgIdentifier.truncateSafe(b, 16);

        assertThat(resA).isNotEqualTo(resB);
    }

    @Test
    @DisplayName("wouldBeTruncated reflète la limite NAMEDATALEN-1 = 63")
    void wouldBeTruncated_thresholdAt63() {
        assertThat(PgIdentifier.wouldBeTruncated("a".repeat(63))).isFalse();
        assertThat(PgIdentifier.wouldBeTruncated("a".repeat(64))).isTrue();
        assertThat(PgIdentifier.wouldBeTruncated(null)).isFalse();
    }

    @Test
    @DisplayName("Contrats : null / suffixe négatif / suffixe trop grand → IllegalArgumentException")
    void truncateSafe_invalidArguments_throw() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PgIdentifier.truncateSafe(null, 0))
                .withMessageContaining("identifier");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PgIdentifier.truncateSafe("foo", -1))
                .withMessageContaining("reservedSuffixLength");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PgIdentifier.truncateSafe("foo", 63))
                .withMessageContaining("no room");
        // Si le suffixe ne laisse pas la place au préfixe + hash ( 9 octets )
        assertThatIllegalArgumentException()
                .isThrownBy(() -> PgIdentifier.truncateSafe("a".repeat(80), 60))
                .withMessageContaining("hash suffix");
    }
}
