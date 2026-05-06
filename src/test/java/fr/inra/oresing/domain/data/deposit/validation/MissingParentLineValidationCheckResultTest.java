package fr.inra.oresing.domain.data.deposit.validation;

import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour MissingParentLineValidationCheckResult.
 */
@Tag("domain.model")
@DisplayName("MissingParentLineValidationCheckResult – tests unitaires")
class MissingParentLineValidationCheckResultTest {

    @Test
    @DisplayName("constructeur crée un résultat ERROR avec les paramètres attendus")
    void constructor() {
        Ltree missingKey = Ltree.fromSql("FR__Paris");
        DataValue.LineIdentityColumnName known = new DataValue.LineIdentityColumnName(
                Ltree.fromSql("FR__Lyon"), Ltree.fromSql("FR.Lyon"), "myRef");
        MissingParentLineValidationCheckResult result = new MissingParentLineValidationCheckResult(
                42L, "refType", missingKey, Set.of(known));
        assertThat(result.level()).isEqualTo(ValidationLevel.ERROR);
        assertThat(result.message()).isEqualTo(MissingParentLineValidationCheckResult.MISSING_PARENT_LINE_IN_RECURSIVE_REFERENCE);
        assertThat(result.messageParams()).containsKey("lineNumber");
        assertThat(result.messageParams()).containsKey("reference");
        assertThat(result.messageParams().get("missingReferencesKey")).isEqualTo("FR__Paris");
    }

    @Test
    @DisplayName("target() retourne null (non renseigné)")
    void targetIsNull() {
        Ltree missingKey = Ltree.fromSql("FR__Bordeaux");
        MissingParentLineValidationCheckResult result = new MissingParentLineValidationCheckResult(
                1L, "ref", missingKey, Set.of());
        assertThat(result.target()).isNull();
    }

    @Test
    @DisplayName("isError() retourne true")
    void isError() {
        Ltree missingKey = Ltree.fromSql("FR__Marseille");
        MissingParentLineValidationCheckResult result = new MissingParentLineValidationCheckResult(
                1L, "ref", missingKey, Set.of());
        assertThat(result.isError()).isTrue();
    }
}