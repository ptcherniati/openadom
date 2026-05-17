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

    /**
     * Extrait l'ensemble des naturalkeys composites distinctes du CSV ,
     * une par ligne , en concatenant les valeurs des colonnes ordonnees
     * dans {@code naturalKeyColumns} avec {@code separator} entre chaque
     * composante . Brique pour l'Axe B ( wiring NaturalKeyPreScan dans
     * DataImporter checker path ) : permet de pre-scanner un CSV de
     * datatype recursif pour determiner quelles naturalkeys ( = identite
     * de ligne ) seront utilisees par {@code WithRecursion} , afin de
     * charger lazy uniquement ces rows depuis {@code referencevalue} via
     * {@code DataRepository.getDataIdPerKeysByNaturalKeys} au lieu du
     * full preload via {@code getDataIdPerKeys} .
     *
     * <h2>Streaming + bornee</h2>
     *
     * <p>Parse le CSV au fil de l'eau ( pas de materialisation complete ) .
     * Sortie : {@code Set<String>} bornee au nombre de naturalkeys
     * distinctes ( typiquement << taille du fichier ; sur les imports
     * recursifs reels , borne par le nombre de noeuds dans la hierarchie ) .
     *
     * <h2>Lignes vides ignorees</h2>
     *
     * <p>Si TOUTES les composantes d'une ligne sont vides apres
     * {@code strip()} , la ligne est ignoree ( aucune naturalkey valide
     * possible ) . Sinon les composantes vides individuelles sont
     * conservees ( ex : "a__" pour 2 columns ou seul col1 a une valeur ) ;
     * la composition reste deterministe et reproductible cote ingestion
     * downstream qui applique la meme regle .
     *
     * <h2>Limitations</h2>
     *
     * <ul>
     *   <li>Pas de normalisation Ltree-friendly ( whitespace -> underscore ,
     *       lowercase , etc . ) appliquee a la composition ici . Si la
     *       config du datatype applique une normalisation , le caller doit
     *       la reproduire avant lookup BDD pour matcher les rows
     *       referencevalue .</li>
     *   <li>Les colonnes absentes du CSV sont silencieusement traitees
     *       comme valeurs vides ( pas d'exception ) .</li>
     * </ul>
     *
     * @param csvReader            lecteur CSV ( ferme par le caller )
     * @param format               format CSV ( delimiter , etc . )
     * @param naturalKeyColumns    colonnes ordonnees formant la natural
     *                             key composite ( cf {@code StandardDataDescription.naturalKey()} )
     * @param separator            separateur entre composantes ( typiquement
     *                             {@code "__"} ; cf
     *                             {@code AsynchroneFileImporterContext.COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR} )
     * @return set des naturalkeys composites distinctes ; vide si les
     *         colonnes sont absentes du CSV ou si toutes les lignes sont
     *         entierement vides
     * @throws IOException si erreur lecture I/O
     * @since openadom plan resilience Axe B
     */
    public Set<String> extractCompositeNaturalKeys(Reader csvReader,
                                                    CSVFormat format,
                                                    java.util.List<String> naturalKeyColumns,
                                                    String separator) throws IOException {
        if (naturalKeyColumns == null || naturalKeyColumns.isEmpty()) {
            return Collections.emptySet();
        }
        if (separator == null) {
            separator = "";
        }
        long t0 = System.nanoTime();
        long rowsScanned = 0L;
        Set<String> result = new HashSet<>();
        CSVFormat withHeader = format.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = CSVParser.parse(csvReader, withHeader)) {
            for (CSVRecord record : parser) {
                rowsScanned++;
                String composed = composeCompositeNaturalKey(record, naturalKeyColumns, separator);
                if (composed == null) {
                    continue;
                }
                // Skip lignes entierement vides ( separateur uniquement ) .
                // Une seule composante vide est gardee si une autre est non-vide
                // ( la valeur composee finale n'est pas constituee que de
                // separateurs ) .
                if (composed.isEmpty() || composed.replace(separator, "").isEmpty()) {
                    continue;
                }
                result.add(composed);
            }
        }
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        if (log.isDebugEnabled()) {
            log.debug("extractCompositeNaturalKeys : {} rows scanned in {} ms ; {} distinct composites for columns {}",
                    rowsScanned, elapsedMs, result.size(), naturalKeyColumns);
        }
        return result;
    }
}
