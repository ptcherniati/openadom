package fr.inra.oresing.rest.data.migration;

import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationData;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import org.javers.core.Javers;
import org.javers.core.diff.Diff;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie le contrat de la configuration Javers utilisée par
 * {@link MigrationConfiguration#javers()} : les changements purement
 * i18n ( libellés , patterns d'affichage , validations / exceptions /
 * components / submissions traduits ) ne doivent pas être détectés
 * comme des changements à valider , de sorte que
 * {@code MigrationService.executeMigration} les laisse traverser et
 * que {@code repository.application().store()} persiste les nouvelles
 * valeurs sans intervention du moteur de règles.
 *
 * <p>Aucun changement structurel ( DDL ) n'étant impliqué par ces
 * modifications , il est préférable de les ignorer côté Javers plutôt
 * que de les router vers une règle « no-op ».
 */
@Tag("core.config")
@Tag("domain.model")
class MigrationConfigurationTest {

    private final Javers javers = new MigrationConfiguration().javers();

    @Test
    void i18nDisplayPattern_changeIsIgnored() {
        InternationalizationData oldData = withDisplayPatternTitle("Ancien titre");
        InternationalizationData newData = withDisplayPatternTitle("Nouveau titre");

        Diff diff = javers.compare(oldData, newData);

        assertThat(diff.getChanges())
                .as("Un changement de i18nDisplayPattern.title ne doit pas être détecté "
                        + "par Javers ; il est censé être ignoré pour traverser silencieusement "
                        + "le pipeline de migration.")
                .isEmpty();
    }

    @Test
    void validations_i18nLabelChangeIsIgnored() {
        InternationalizationData oldData = new InternationalizationData();
        oldData.setValidations(Map.of(
                "rangeCheck", Map.of(Locale.FRENCH, "Valeur hors borne")
        ));

        InternationalizationData newData = new InternationalizationData();
        newData.setValidations(Map.of(
                "rangeCheck", Map.of(Locale.FRENCH, "Valeur en dehors de l'intervalle autorisé")
        ));

        Diff diff = javers.compare(oldData, newData);

        assertThat(diff.getChanges())
                .as("Un changement de libellé i18n d'une validation ne doit pas être détecté "
                        + "par Javers ; il est censé être ignoré pour traverser silencieusement "
                        + "le pipeline de migration.")
                .isEmpty();
    }

    private static InternationalizationData withDisplayPatternTitle(String title) {
        InternationalizationData data = new InternationalizationData();
        InternationalizationTitle pattern = new InternationalizationTitle();
        pattern.setTitle(Map.of(Locale.FRENCH, title));
        data.setI18nDisplayPattern(pattern);
        return data;
    }
}
