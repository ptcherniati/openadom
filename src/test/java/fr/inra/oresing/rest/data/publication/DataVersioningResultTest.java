package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires pour {@link DataVersioningResult}.
 *
 * <p>Couvre :
 * <ul>
 *   <li>constructeur compact (Objects.requireNonNull, List.copyOf)</li>
 *   <li>constructeur historique sans uploadState</li>
 *   <li>factory {@code of()} (2 surcharges)</li>
 *   <li>{@code withDataSynthesis()}</li>
 *   <li>immutabilité de la liste dataSynthesis</li>
 * </ul>
 */
@Tag("domain.model")
@DisplayName("DataVersioningResult — record immuable de versioning")
class DataVersioningResultTest {

    private static final UUID DATA_ID = UUID.randomUUID();

    // ─── constructeur compact ─────────────────────────────────────────────────

    @Test
    @DisplayName("dataId null → NullPointerException")
    void dataIdNullThrows() {
        List<ApplicationResult.DataSynthesis> emptyList = List.of();
        assertThatThrownBy(() -> new DataVersioningResult(null, emptyList, "/uri", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("dataSynthesis null → remplacé par List.of() (défensif)")
    void dataSynthesisNullBecomesEmpty() {
        DataVersioningResult r = new DataVersioningResult(DATA_ID, null, "/uri");
        assertThat(r.dataSynthesis()).isEmpty();
    }

    @Test
    @DisplayName("dataSynthesis passé en paramètre → copié (immuable)")
    void dataSynthesisCopied() {
        List<ApplicationResult.DataSynthesis> mutable = new ArrayList<>();
        DataVersioningResult r = new DataVersioningResult(DATA_ID, mutable, "/uri");
        // Mutating original list does not affect the record
        mutable.add(new ApplicationResult.DataSynthesis());
        assertThat(r.dataSynthesis()).isEmpty();
    }

    // ─── constructeur historique ──────────────────────────────────────────────

    @Test
    @DisplayName("constructeur 3-args → uploadState null")
    void legacyConstructor() {
        DataVersioningResult r = new DataVersioningResult(DATA_ID, List.of(), "/legacy");
        assertThat(r.dataId()).isEqualTo(DATA_ID);
        assertThat(r.uri()).isEqualTo("/legacy");
        assertThat(r.uploadState()).isNull();
    }

    // ─── factory of() ────────────────────────────────────────────────────────

    @Test
    @DisplayName("of(nameOrId, dataName, dataId, synthesis) → URI correctement encodée")
    void ofFactory4Args() {
        DataVersioningResult r = DataVersioningResult.of(
                "myApp", "dataType", DATA_ID, List.of());
        assertThat(r.dataId()).isEqualTo(DATA_ID);
        assertThat(r.uri()).isNotNull();
        assertThat(r.uploadState()).isNull();
    }

    @Test
    @DisplayName("of(nameOrId, dataName, dataId, synthesis, uploadState) → uploadState conservé")
    void ofFactory5Args() {
        DataVersioningResult r = DataVersioningResult.of(
                "app", "ref", DATA_ID, List.of(), EmailService.UPLOAD_STATE.PUBLISHED);
        assertThat(r.uploadState()).isEqualTo(EmailService.UPLOAD_STATE.PUBLISHED);
    }

    @Test
    @DisplayName("of() encode les caractères spéciaux dans l'URI")
    void ofFactoryEncodesUri() {
        DataVersioningResult r = DataVersioningResult.of(
                "app with spaces", "data/type", DATA_ID, List.of());
        // L'URI ne doit pas contenir d'espaces bruts
        assertThat(r.uri()).doesNotContain(" ");
    }

    // ─── withDataSynthesis() ─────────────────────────────────────────────────

    @Test
    @DisplayName("withDataSynthesis() retourne un nouveau record avec la liste mise à jour")
    void withDataSynthesis() {
        DataVersioningResult original = new DataVersioningResult(DATA_ID, List.of(), "/uri");

        ApplicationResult.DataSynthesis ds = new ApplicationResult.DataSynthesis();
        DataVersioningResult updated = original.withDataSynthesis(List.of(ds));

        assertThat(updated.dataSynthesis()).hasSize(1);
        assertThat(updated.dataId()).isEqualTo(DATA_ID);
        assertThat(updated.uri()).isEqualTo("/uri");
        // original non muté
        assertThat(original.dataSynthesis()).isEmpty();
    }

    @Test
    @DisplayName("withDataSynthesis() préserve uploadState")
    void withDataSynthesisPreservesUploadState() {
        DataVersioningResult original = new DataVersioningResult(
                DATA_ID, List.of(), "/uri", EmailService.UPLOAD_STATE.UPLOADED);
        DataVersioningResult updated = original.withDataSynthesis(List.of());
        assertThat(updated.uploadState()).isEqualTo(EmailService.UPLOAD_STATE.UPLOADED);
    }
}