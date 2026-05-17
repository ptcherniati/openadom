package fr.inra.oresing.domain.data.deposit.prescan;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Pre-scanner CSV : extrait l'ensemble des valeurs distinctes par colonne
 * d'interet , a usage du chargement parresseux ( lazy load ) des refs
 * existantes en base ( resilience volumes plan - Axe A + B ) .
 *
 * <h2>Pourquoi</h2>
 *
 * <p>Le chargement actuel des refs foreign ( {@code findAllByReferenceType} )
 * et self ( {@code getDataIdPerKeys} ) materialise la totalite de la table
 * referencevalue du refType en RAM Java . Sur des refs de 1M-100M+ rows
 * cela peut depasser le heap JVM ( ~2 GB par 3.6M rows ) et provoquer
 * des OOM .
 *
 * <p>Strategie alternative : ne charger en RAM que les naturalkeys
 * effectivement referencees dans le CSV soumis a publication . Le pre-scan
 * fait deux passes legeres sur le CSV :
 * <ol>
 *   <li>1ere passe ( ce service ) : extraire les valeurs distinctes des
 *       colonnes d'interet . Pas de validation , pas de checker , juste
 *       lecture stream + collecte en {@code Set} . Cout : ~3-10s sur 1M
 *       lignes selon densite des refs .</li>
 *   <li>2eme passe ( cascade normale ) : traitement complet avec les refs
 *       deja chargees en LRU bornee .</li>
 * </ol>
 *
 * <h2>Garanties</h2>
 * <ul>
 *   <li>Streaming : ne materialise pas le CSV complet en RAM , parse au
 *       fil de l'eau .</li>
 *   <li>Bornee : la sortie est un {@code Set<String>} par colonne ,
 *       proportionnel au nombre de valeurs distinctes ( typique << M ) .</li>
 *   <li>Stateless : pas de cache , pas de dependance Spring ; instanciable
 *       directement pour tests .</li>
 * </ul>
 *
 * <h2>Limitations</h2>
 * <ul>
 *   <li>Le scan considere la valeur "brute" du CSV . Toute normalisation
 *       ( trim , lowercase , Ltree-sanitize ) eventuelle doit etre faite
 *       par le caller avant lookup en BDD .</li>
 *   <li>Les cellules vides ( "" ou whitespace ) sont exclues du set
 *       retourne ( pas de naturalkey vide en BDD ) .</li>
 *   <li>La derniere ligne sans newline trailing est inclue normalement .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
@Slf4j
public final class NaturalKeyPreScanService {

    /**
     * Pre-scan le CSV et retourne pour chaque colonne d'interet l'ensemble
     * des valeurs distinctes non-vides rencontrees .
     *
     * @param csvReader            lecteur CSV ( typiquement obtenu via
     *                             {@code Files.newBufferedReader(path, UTF_8)} ) ;
     *                             ferme par le caller
     * @param format               format CSV ( delimiter , skipHeaderRecord ,
     *                             quote , etc . ) - le caller doit fournir
     *                             le format aligne sur le datatype
     * @param columnsOfInterest    noms d'entetes ( = headers du CSV ) dont
     *                             on veut extraire les valeurs distinctes ;
     *                             les colonnes absentes du CSV sont
     *                             ignorees silencieusement
     * @return mapping {@code columnHeader -> Set<distinct values>} ; les
     *         colonnes sans aucune valeur non-vide retournent un Set vide ;
     *         les colonnes inexistantes dans le CSV ne sont PAS presentes
     *         dans le mapping
     * @throws IOException si erreur de lecture I/O
     */
    public Map<String, Set<String>> extractDistinctValues(Reader csvReader,
                                                          CSVFormat format,
                                                          Set<String> columnsOfInterest) throws IOException {
        if (columnsOfInterest == null || columnsOfInterest.isEmpty()) {
            return Collections.emptyMap();
        }
        long t0 = System.nanoTime();
        long rowsScanned = 0L;
        Map<String, Set<String>> result = new HashMap<>();

        // Format avec header pour acceder aux colonnes par nom .
        CSVFormat withHeader = format.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = CSVParser.parse(csvReader, withHeader)) {
            // Filtre les colonnes d'interet effectivement presentes dans le CSV
            // ( evite NullPointerException sur record.get(missingHeader) ) .
            Set<String> headers = parser.getHeaderNames() != null
                    ? new LinkedHashSet<>(parser.getHeaderNames())
                    : Collections.emptySet();
            Set<String> presentColumns = columnsOfInterest.stream()
                    .filter(headers::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (String col : presentColumns) {
                result.put(col, new HashSet<>());
            }
            if (presentColumns.isEmpty()) {
                return Collections.emptyMap();
            }
            for (CSVRecord record : parser) {
                rowsScanned++;
                for (String col : presentColumns) {
                    String value = record.get(col);
                    if (value == null) continue;
                    String trimmed = value.strip();
                    if (trimmed.isEmpty()) continue;
                    result.get(col).add(trimmed);
                }
            }
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        if (log.isDebugEnabled()) {
            log.debug("NaturalKeyPreScan : {} rows scanned in {} ms ; distinct counts per column : {}",
                    rowsScanned, elapsedMs,
                    result.entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().size())));
        }
        return result;
    }

    /**
     * Pre-scan le CSV pour UNE seule colonne d'interet ( raccourci
     * pratique ) . Retourne {@link Collections#emptySet()} si la
     * colonne est absente du CSV .
     */
    public Set<String> extractDistinctValues(Reader csvReader,
                                             CSVFormat format,
                                             String columnHeader) throws IOException {
        Map<String, Set<String>> all = extractDistinctValues(csvReader, format, Set.of(columnHeader));
        return all.getOrDefault(columnHeader, Collections.emptySet());
    }

    /** Concatene N colonnes d'une ligne avec un separateur ( utile pour
     *  composer la naturalkey self-ref a partir de plusieurs colonnes du
     *  CSV , equivalent au composite naturalKey du config datatype ) . */
    public static String composeCompositeNaturalKey(CSVRecord record,
                                                    java.util.List<String> columns,
                                                    String separator) {
        if (record == null || columns == null || columns.isEmpty()) {
            return "";
        }
        return columns.stream()
                .map(col -> {
                    String v = record.isMapped(col) ? record.get(col) : "";
                    return v == null ? "" : v.strip();
                })
                .collect(Collectors.joining(separator));
    }

    /**
     * Variante streamable : pour traiter le CSV chunk-par-chunk au cas ou
     * memorisere meme les Set distinct serait trop ( volumetrie tres
     * elevee ) . Le caller fournit un consumer qui voit chaque {@link
     * CSVRecord} ; le service gere juste le streaming + l'access par
     * header . Pas d'allocation Set interne .
     */
    public void streamRecords(Reader csvReader,
                              CSVFormat format,
                              java.util.function.Consumer<CSVRecord> recordConsumer) throws IOException {
        CSVFormat withHeader = format.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = CSVParser.parse(csvReader, withHeader)) {
            StreamSupport.stream(parser.spliterator(), false).forEach(recordConsumer);
        }
    }
}
