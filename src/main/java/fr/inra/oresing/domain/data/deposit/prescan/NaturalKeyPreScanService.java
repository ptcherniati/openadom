package fr.inra.oresing.domain.data.deposit.prescan;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
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
@Component
public class NaturalKeyPreScanService {

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
            for (CSVRecord csvRow : parser) {
                rowsScanned++;
                for (String col : presentColumns) {
                    String value = csvRow.get(col);
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
    public static String composeCompositeNaturalKey(CSVRecord csvRow,
                                                    java.util.List<String> columns,
                                                    String separator) {
        if (csvRow == null || columns == null || columns.isEmpty()) {
            return "";
        }
        return columns.stream()
                .map(col -> {
                    String v = csvRow.isMapped(col) ? csvRow.get(col) : "";
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
        return extractCompositeNaturalKeys(csvReader, format, naturalKeyColumns, separator, null, null);
    }

    /**
     * Overload qui respecte {@code OA_dataHeaderLine} / {@code OA_dataFirstLine}
     * du datatype . Indispensable pour les CSV "Excel-style" ou les N-1
     * premieres lignes sont de la metadata humain ( titre , site , commentaire ,
     * separateurs vides , etc . ) et le vrai header technique est decale a la
     * ligne {@code dataHeaderLine} ( typiquement 8 pour le layout ACBB ) .
     *
     * <p>Sans cette prise en compte , {@link CSVParser} parserait la ligne 1
     * comme header , detecterait des cellules vides ( "A header name is missing" )
     * et abortait le prescan -> bug
     * {@code ISSUE_GITLAB_PRESCAN_AXE_B_IGNORE_DATAHEADERLINE_2026-05-18} .
     *
     * <h2>Algorithme</h2>
     *
     * <ol>
     *   <li>Skip {@code dataHeaderLine - 1} lignes brutes via
     *       {@code BufferedReader.readLine()} ( pas de parsing CSV ) ;</li>
     *   <li>Strip BOM UTF-8 ( {@code ﻿} ) eventuel en tete de la 1ere
     *       ligne lue ( les CSV exportes depuis Excel commencent souvent par
     *       le BOM ) ;</li>
     *   <li>Laisse {@code CSVParser} consommer la ligne suivante comme header
     *       ( setSkipHeaderRecord=true ) ;</li>
     *   <li>Skip {@code dataFirstLine - dataHeaderLine - 1} CSV records
     *       supplementaires ( lignes description / type / obligatoire entre
     *       le header et la 1ere ligne data ) ;</li>
     *   <li>Itere les records restants et compose les naturalkeys composites .</li>
     * </ol>
     *
     * <p>Si {@code dataHeaderLine} est {@code null} ou {@code <= 1} , le
     * comportement est identique a la legacy ( header sur ligne 1 ) . Idem
     * pour {@code dataFirstLine == null} : aucun skip post-header .
     *
     * @param dataHeaderLine numero ( 1-indexed ) de la ligne header dans le
     *                       CSV ; {@code null} = ligne 1 ( default )
     * @param dataFirstLine  numero ( 1-indexed ) de la 1ere ligne data ;
     *                       {@code null} = immediatement apres le header
     */
    public Set<String> extractCompositeNaturalKeys(Reader csvReader,
                                                    CSVFormat format,
                                                    java.util.List<String> naturalKeyColumns,
                                                    String separator,
                                                    Integer dataHeaderLine,
                                                    Integer dataFirstLine) throws IOException {
        if (naturalKeyColumns == null || naturalKeyColumns.isEmpty()) {
            return Collections.emptySet();
        }
        if (separator == null) {
            separator = "";
        }
        long t0 = System.nanoTime();
        long rowsScanned = 0L;
        Set<String> result = new HashSet<>();

        int headerLineIdx = (dataHeaderLine == null || dataHeaderLine < 1) ? 1 : dataHeaderLine;
        int firstLineIdx  = (dataFirstLine  == null) ? headerLineIdx + 1
                : Math.max(headerLineIdx + 1, dataFirstLine);
        int recordsToSkipAfterHeader = firstLineIdx - headerLineIdx - 1;

        BufferedReader br = (csvReader instanceof BufferedReader)
                ? (BufferedReader) csvReader
                : new BufferedReader(csvReader);

        // Skip les ( headerLineIdx - 1 ) premieres lignes brutes ( metadata humain
        // Excel-style : titre , site , commentaire , separateurs vides ) avant
        // de laisser CSVParser parser le header .
        for (int i = 1; i < headerLineIdx; i++) {
            String discarded = br.readLine();
            if (discarded == null) {
                // CSV plus court que dataHeaderLine -> rien a scanner .
                return Collections.emptySet();
            }
        }

        // Strip BOM UTF-8 en tete de la 1ere ligne effectivement lue par
        // CSVParser ( header ) . Sans ce strip , le BOM est inclus dans le
        // nom de la 1ere colonne header ( ex "﻿type_zone" ) et toutes
        // les references record.get("type_zone") echouent .
        Reader effective = new BomStrippingReader(br);

        CSVFormat withHeader = format.builder().setHeader().setSkipHeaderRecord(true).get();
        try (CSVParser parser = CSVParser.parse(effective, withHeader)) {
            var iterator = parser.iterator();
            // Skip les lignes entre header et 1ere data ( description , type ,
            // obligatoire ) - elles sont parsees comme records mais ne portent
            // pas de naturalkey valide .
            for (int i = 0; i < recordsToSkipAfterHeader && iterator.hasNext(); i++) {
                iterator.next();
            }
            while (iterator.hasNext()) {
                CSVRecord csvRow = iterator.next();
                rowsScanned++;
                String composed = composeCompositeNaturalKey(csvRow, naturalKeyColumns, separator);
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
            log.debug("extractCompositeNaturalKeys : {} rows scanned in {} ms ; {} distinct composites for columns {} ( headerLine={} firstLine={} )",
                    rowsScanned, elapsedMs, result.size(), naturalKeyColumns,
                    headerLineIdx, firstLineIdx);
        }
        return result;
    }

    /**
     * Reader decorator qui strip un BOM UTF-8 ( {@code ﻿} ) eventuel en
     * tete du flux . Indispensable avant {@link CSVParser} car celui-ci
     * inclut le BOM dans le nom de la 1ere colonne header si present , ce
     * qui casse silencieusement tous les {@code record.get(headerName)}
     * downstream .
     *
     * <p>Implementation : 1 char de lookahead sur le premier {@code read()} ;
     * si == {@code ﻿} on l'ignore , sinon on le buffer et on le rend
     * au prochain appel .
     */
    static final class BomStrippingReader extends Reader {
        private final Reader delegate;
        private boolean firstRead = true;
        private int buffered = -1;

        BomStrippingReader(Reader delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read(char[] cbuf, int off, int len) throws IOException {
            if (firstRead) {
                firstRead = false;
                int c = delegate.read();
                if (c == -1) return -1;
                if (c != '\uFEFF') {
                    buffered = c;
                }
            }
            int written = 0;
            if (buffered != -1 && written < len) {
                cbuf[off + written++] = (char) buffered;
                buffered = -1;
            }
            if (written < len) {
                int n = delegate.read(cbuf, off + written, len - written);
                if (n > 0) {
                    written += n;
                } else if (written == 0) {
                    return n;
                }
            }
            return written;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}