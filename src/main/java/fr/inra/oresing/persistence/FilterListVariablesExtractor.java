package fr.inra.oresing.persistence;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.data.RefsLinked;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Helper pur (zéro dépendance Spring/JDBC) qui dérive l'ensemble des
 * componentKeys filtrables d'un datatype à partir du payload {@code /filters}
 * déjà calculé + de la configuration de l'application .
 *
 * <p><b>Pourquoi</b> : le payload {@code /filters} exposé au frontend doit
 * contenir une liste {@code variables} = ensemble des componentKeys que le
 * bloc filtre frontend doit rendre . Le frontend utilise cette liste pour
 * peupler {@code columns.value} dès l'arrivée de {@code /filters} ,
 * SANS attendre {@code /data} ( gain UX gros datasets ) .
 *
 * <p><b>Source iso-FilterList</b> ( garantie cruciale ) : on dérive
 * directement des {@link FilterListEntry} déjà calculés au lieu d'aller
 * lire les keys JSON {@code refvalues} d'une row arbitraire ( ancien
 * algorithme {@code /data} ) . Cette iso-source élimine la divergence
 * silencieuse responsable du bug "aucun filtre disponible" : si une
 * column est dans {@code variables} , alors elle est forcément matchable
 * par {@code ReferenceFilter.options.filter(node => node.components.includes(componentKey))}
 * côté frontend ( car {@code components} et {@code variables} viennent du
 * MÊME calcul SQL ) .
 *
 * <p><b>Trois sources contribuent</b> :
 * <ol>
 *   <li>{@link FilterList#refsLinkeds()} : pour chaque leaf , aplatir
 *       {@code components[]} ( componentKeys datatype qui pointent vers
 *       cette ref ) . Couvre {@code ReferenceChecker} .</li>
 *   <li>{@link ColumnDistinctValues#componentKey()} : la column elle-même .
 *       Couvre {@code __FILTER_LIST__} et {@code __FILTER_TEXT__} ( tags
 *       opt-in ) .</li>
 *   <li>{@code componentDescriptions} avec checker
 *       {@code FloatChecker / IntegerChecker / DateChecker} : pas
 *       d'entrée dans {@code /filters} ( pas de DISTINCT pré-calculé ) ,
 *       mais affichent quand même un filtre intervalle from/to côté
 *       frontend ( ne dépend pas du payload filterList ) . On les ajoute
 *       depuis le config pour ne pas masquer ces colonnes du bloc filtre .</li>
 * </ol>
 *
 * <p><b>Filtre hidden / lang-restriction</b> : aligné avec l'algo
 * historique de {@code OreSiResources.getAllDataJson} ( filtre
 * {@code isHiddenOrHasLangRestriction(language)} ) - une colonne cachée
 * par config YAML n'est jamais exposée au frontend , quel que soit
 * l'état des données .
 *
 * <p>Résultat TreeSet trié pour déterminisme ( payload + ETag stables ) .
 *
 * @author R.YAHIAOUI
 * @since 2026-05-23 ( cache filter columns )
 */
public final class FilterListVariablesExtractor {

    private static final Set<CheckerDescription.CheckerDescriptionType> INTERVAL_CHECKER_TYPES =
            Set.of(
                    CheckerDescription.CheckerDescriptionType.FloatChecker,
                    CheckerDescription.CheckerDescriptionType.IntegerChecker,
                    CheckerDescription.CheckerDescriptionType.DateChecker);

    private FilterListVariablesExtractor() {
        // utilitaire pur , pas d'instance
    }

    /**
     * Calcule l'ensemble des componentKeys filtrables pour un datatype .
     *
     * @param entries     entrées de {@code /filters} déjà calculées
     *                    ( {@code FilterList} + {@code ColumnDistinctValues} )
     * @param application application courante ( fournit le config pour
     *                    récupérer les checkers interval + filtre hidden )
     * @param refType     nom du datatype ( = referenceType en base )
     * @param language    langue courante pour le filtre hidden / lang-restriction
     * @return ensemble trié des componentKeys filtrables , jamais {@code null}
     *         ( vide si rien à exposer )
     */
    public static Set<String> extract(
            final List<FilterListEntry> entries,
            final Application application,
            final String refType,
            final String language) {
        final Set<String> variables = new TreeSet<>();

        // 1. Sources /filters payload
        if (entries != null) {
            for (final FilterListEntry entry : entries) {
                if (entry == null) continue;
                switch (entry) {
                    case FilterList filterList -> collectFromFilterList(filterList, variables);
                    case ColumnDistinctValues cdv -> {
                        if (cdv.componentKey() != null) variables.add(cdv.componentKey());
                    }
                    default -> {
                        // type inconnu : on ignore silencieusement plutôt
                        // que de lever , pour rester forward-compat sur
                        // ajout futur de FilterListEntry implementations
                    }
                }
            }
        }

        // 2. Colonnes interval ( Float / Integer / Date ) depuis le config
        //    Elles n'ont pas d'entrée /filters mais doivent quand même apparaître
        //    dans le bloc filtre frontend ( rendu en interval input from/to ) .
        final Optional<StandardDataDescription> dataDescriptionOpt = application.findData(refType);
        if (dataDescriptionOpt.isPresent()) {
            final StandardDataDescription dataDescription = dataDescriptionOpt.get();
            final Map<String, ComponentDescription> componentDescriptions = dataDescription.componentDescriptions();
            if (componentDescriptions != null) {
                for (final Map.Entry<String, ComponentDescription> e : componentDescriptions.entrySet()) {
                    final String componentKey = e.getKey();
                    final ComponentDescription cd = e.getValue();
                    if (cd == null) continue;
                    if (isIntervalChecker(cd) && !isHidden(cd, language)) {
                        variables.add(componentKey);
                    }
                }
            }
        }

        return ImmutableSet.copyOf(variables);
    }

    private static void collectFromFilterList(final FilterList filterList, final Set<String> sink) {
        final List<RefsLinked> leaves = filterList.refsLinkeds();
        if (leaves == null) return;
        for (final RefsLinked leaf : leaves) {
            if (leaf == null) continue;
            final List<String> components = leaf.components();
            if (components == null) continue;
            for (final String c : components) {
                if (c != null && !c.isBlank()) sink.add(c);
            }
        }
    }

    private static boolean isIntervalChecker(final ComponentDescription cd) {
        final CheckerDescription checker = cd.checker();
        if (checker == null) return false;
        return INTERVAL_CHECKER_TYPES.contains(checker.type());
    }

    private static boolean isHidden(final ComponentDescription cd, final String language) {
        try {
            return cd.isHiddenOrHasLangRestriction(language);
        } catch (Exception e) {
            // Best-effort : si le helper de config lève ( config corrompue ) ,
            // on considère non hidden plutôt que de faire échouer le payload .
            return false;
        }
    }
}
