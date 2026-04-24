package fr.inra.oresing.rest.model.data;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.NullType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.RefsLinkedToValue;
import fr.inra.oresing.persistence.DataRow;
import org.apache.commons.collections.keyvalue.DefaultMapEntry;

import java.util.*;
import java.util.stream.Collectors;


public record DataRowResult(
        List<String> rowId,
        String naturalKey,
        String hierarchicalKey,
        Map<String, Object> values,
        List<fr.inra.oresing.persistence.RefsLinked> refsLinkeds, Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo,
        //Long totalRows,
        //Long rowNumber,
        Map<Object, Object> displaysForRow,
        List<String> allPatternColumnName
) {

    public static final String DEFAULT = "default";

    public static DataRowResult of(DataRow dataRow,
                                   ImmutableSet<String> variables,
                                   String locale) {
        final Map<String, Object> rows = new HashMap<>();
        for (final Map.Entry<String, FieldType<?>> componentEntry : dataRow.values().entrySet()) {
            final String component = componentEntry.getKey();
            if (variables.contains(component) || componentEntry.getKey().startsWith(DataColumn.DISPLAY)) {
                rows
                        .put(component, Optional.of(componentEntry)
                                .map(Map.Entry::getValue)
                                .map(FieldType::toJsonForFrontend)
                                .orElse(NullType.INSTANCE));
            }
        }
        // PERF #465 — Construction d'un Map de lookup (referenceType -> naturalKey -> RefsLinked)
        // pour accéder aux noms d'affichage des références en O(1) au lieu de O(n).
        //
        // Avant : pour chaque naturalKey de chaque ligne, on parcourait TOUTE la liste refsLinked
        //         avec stream().filter().findFirst() → O(n × m) pour 500 lignes × 9 refs = ~4500 scans.
        // Après : on construit un Map une seule fois, puis on fait des get() en O(1).
        //
        // Null-safety :
        //   - refsLinked() peut être null pour certaines apps (ex: télédétection sans références)
        //   - referenceType() et naturalKey() peuvent être null en mode horizontalisé (PatternComponent)
        //   - Collectors.groupingBy() ne supporte PAS les clés null → d'où le .filter() avant
        Map<String, Map<String, fr.inra.oresing.persistence.RefsLinked>> refsLinkedMap =
                dataRow.refsLinked() != null
                        ? dataRow.refsLinked().stream()
                                .filter(r -> r.referenceType() != null && r.naturalKey() != null)
                                .collect(Collectors.groupingBy(
                                        fr.inra.oresing.persistence.RefsLinked::referenceType,
                                        Collectors.toMap(
                                                r -> r.naturalKey().getSql(),
                                                r -> r,
                                                (existing, replacement) -> existing
                                        )
                                ))
                        : Map.of();

        // Construction du Map des noms d'affichage localisés pour chaque référence de cette ligne.
        // Pour chaque entrée de refsLinkedTo, on cherche le nom d'affichage (fr/en/default)
        // dans le Map de lookup construit ci-dessus au lieu de scanner toute la liste.
        // refsLinkedTo() peut être null pour certaines apps → on retourne un Map vide dans ce cas.
        Map<Object, Object> displaysForRow = dataRow.refsLinkedTo() != null
                ? dataRow.refsLinkedTo().entrySet().stream()
                .map(referenceEntry -> {
                    String referenceName = referenceEntry.getKey();
                    // Lookup O(1) dans le Map au lieu de stream().filter().findFirst()
                    Map<String, fr.inra.oresing.persistence.RefsLinked> refsByNaturalKey =
                            refsLinkedMap.getOrDefault(referenceName, Map.of());
                    Map<Object, Object> naturalKeysDisplay = referenceEntry.getValue().values().stream()
                            .map(RefsLinkedToValue::hierarchicalKey)
                            .filter(Objects::nonNull)
                            // Extraire la naturalKey depuis la hierarchicalKey (ex: "tr_case_study_cstKcss1" → "css1")
                            .map(hierarchicalKey -> hierarchicalKey.getSql().replaceAll(".*[a-z]K", ""))
                            .map(naturalKey -> {
                                // Lookup O(1) au lieu de stream().filter().findFirst()
                                fr.inra.oresing.persistence.RefsLinked refsLinked = refsByNaturalKey.get(naturalKey);
                                String displayValue;
                                if (refsLinked != null) {
                                    // Choisir le nom d'affichage selon la locale (fr ou en), avec fallback sur default
                                    displayValue = locale.equals(Locale.FRENCH.getLanguage()) ? refsLinked.__display_fr() : refsLinked.__display_en();
                                    if (displayValue == null) {
                                        displayValue = refsLinked.__display_default();
                                    }
                                } else {
                                    // Pas de référence trouvée → afficher la naturalKey brute
                                    displayValue = naturalKey;
                                }
                                return new DefaultMapEntry(naturalKey, displayValue);
                            })
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> existing));
                    return new DefaultMapEntry(referenceName, naturalKeysDisplay);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (existing, replacement) -> existing))
                : Map.of();
        return new DataRowResult(dataRow.rowId(),
                dataRow.naturalKey() != null ? dataRow.naturalKey().getSql() : null,
                dataRow.hierarchicalKey() != null ? dataRow.hierarchicalKey().getSql() : null,
                rows,
                dataRow.refsLinked(),
                dataRow.refsLinkedTo(),
                displaysForRow,
                dataRow.allPatternColumnNames());
    }
}