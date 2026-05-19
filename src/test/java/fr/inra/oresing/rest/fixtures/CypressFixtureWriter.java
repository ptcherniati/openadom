package fr.inra.oresing.rest.fixtures;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaire pour générer les fixtures Cypress à partir des réponses de l'API de test.
 *
 * <p>Cette classe remplit deux objectifs :
 * <ol>
 *   <li><strong>Chemin configurable</strong> via la propriété système
 *       {@code cypress.fixtures.base.dir} (valeur par défaut : répertoire courant du JVM,
 *       c'est-à-dire la racine du projet Maven lors d'un run local).</li>
 *   <li><strong>Normalisation des UUID volatils</strong> : les UUID générés aléatoirement
 *       (application, utilisateurs) sont remplacés par des UUID stables prédéfinis afin que
 *       les fixtures soient identiques d'une exécution à l'autre et puissent être committées
 *       en VCS.</li>
 * </ol>
 *
 * <p>Les UUID stables sont définis comme constantes publiques de cette classe ; ils sont
 * purement fictifs (préfixe {@code a0000…}) et ne doivent apparaître que dans les fixtures
 * de test Cypress, jamais dans un vrai environnement.
 *
 * <h2>Usage typique</h2>
 * <pre>{@code
 * // Dans CypressFixtureGeneratorTest.init() :
 * CypressFixtureWriter writer = CypressFixtureWriter.defaultWriter()
 *     .withUuid(fixtures.getMonsoresimpleConnection().userResult().userId().toString(),
 *               CypressFixtureWriter.MONSORESIMPLE_USER_UUID)
 *     .withUuid(appId, CypressFixtureWriter.MONSORESIMPLE_APP_UUID);
 *
 * // Écriture d'une fixture :
 * writer.write("ui/cypress/fixtures/applications/ore/monsore/monsoere.json", responseBody);
 *
 * // Écriture du fichier d'alias (optionnel, pour debug Cypress) :
 * writer.writeAliases("ui/cypress/fixtures/applications/ore/aliases.json");
 * }</pre>
 */
@Slf4j
public class CypressFixtureWriter {

    // ── UUID stables (fictifs) ────────────────────────────────────────────
    /** UUID stable de l'utilisateur {@code monsoresimple}. */
    public static final String MONSORESIMPLE_USER_UUID  = "a0000001-0000-0000-0000-000000000001";
    /** UUID stable de l'utilisateur {@code withrigths}. */
    public static final String WITHRIGTHS_USER_UUID     = "a0000001-0000-0000-0000-000000000002";
    /** UUID stable de l'utilisateur {@code lambda}. */
    public static final String LAMBDA_USER_UUID         = "a0000001-0000-0000-0000-000000000003";
    /** UUID stable de l'utilisateur {@code poussin} (admin). */
    public static final String POUSSIN_USER_UUID        = "a0000001-0000-0000-0000-000000000004";
    /** UUID stable de l'application {@code monsoresimple}. */
    public static final String MONSORESIMPLE_APP_UUID   = "a0000100-0000-0000-0000-000000000001";

    // ── Noms logiques (clés dans aliases.json) ────────────────────────────
    public static final String KEY_MONSORESIMPLE_USER = "MONSORESIMPLE_USER_ID";
    public static final String KEY_WITHRIGTHS_USER    = "WITHRIGTHS_USER_ID";
    public static final String KEY_LAMBDA_USER        = "LAMBDA_USER_ID";
    public static final String KEY_POUSSIN_USER       = "POUSSIN_USER_ID";
    public static final String KEY_MONSORESIMPLE_APP  = "MONSORESIMPLE_APP_ID";

    /** Propriété système permettant de surcharger le répertoire de base des fixtures. */
    public static final String BASE_DIR_PROPERTY = "cypress.fixtures.base.dir";

    // ── Regex pour détecter les UUID v4 dans le JSON ─────────────────────
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * Regex pour détecter les timestamps ISO-8601 volatils du run courant.
     * Format ciblé : {@code YYYY-MM-DDTHH:MM:SS[.fraction]} (sans préfixe +/-).
     * Les dates métier commencent par +/- ({@code +999999999-…}, {@code -999999999-…}) et ne
     * sont donc pas affectées.
     */
    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}[.\\d]*Z?");

    // ── État interne ──────────────────────────────────────────────────────
    /** UUID dynamique (run courant) → UUID stable (prédéfini). */
    private final Map<String, String> dynamicToStable = new LinkedHashMap<>();
    /** UUID stable → nom logique, pour l'écriture de {@code aliases.json}. */
    private final Map<String, String> stableToKey     = new LinkedHashMap<>();
    /** Compteur pour l'auto-assignation des UUID stables (row IDs, configFile, etc.). */
    private long rowUuidCounter = 0;

    private final Path baseDir;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Constructeurs / factory methods ──────────────────────────────────

    /**
     * Crée un writer dont le répertoire de base est lu depuis la propriété système
     * {@value #BASE_DIR_PROPERTY}.
     *
     * <p>Si la propriété n'est pas définie, retourne un writer <em>no-op</em> dont le
     * répertoire pointe vers {@code /dev/null} (les appels à {@link #write} ne produisent
     * aucun fichier car la propriété absente est détectée dès l'appel).
     * Cela garantit que les fichiers Cypress ne sont écrits que lorsque le profil
     * {@code generate-fixtures} est actif.
     */
    public static CypressFixtureWriter defaultWriter() {
        String baseDirProp = System.getProperty(BASE_DIR_PROPERTY);
        if (baseDirProp == null) {
            log.debug("defaultWriter : propriété {} non définie — writer no-op (aucun fichier ne sera écrit)",
                    BASE_DIR_PROPERTY);
            return new NoOpCypressFixtureWriter();
        }
        return new CypressFixtureWriter(Paths.get(baseDirProp));
    }

    public CypressFixtureWriter(Path baseDir) {
        this.baseDir = baseDir;
    }

    // ── Configuration du mapping ──────────────────────────────────────────

    /**
     * Ajoute un mapping {@code dynamicUuid → stableUuid} <em>et</em> enregistre la clé
     * logique associée pour {@code aliases.json}.
     *
     * @param dynamicUuid UUID généré pendant le run courant (valeur qui change d'un run à l'autre)
     * @param stableUuid  UUID stable prédéfini (constante de cette classe)
     * @param logicalKey  Clé lisible par un humain, ex. {@value #KEY_MONSORESIMPLE_USER}
     * @return {@code this} pour chaînage
     */
    public CypressFixtureWriter withUuid(String dynamicUuid, String stableUuid, String logicalKey) {
        if (dynamicUuid == null || dynamicUuid.isBlank()) {
            log.warn("withUuid() : UUID dynamique nul ou vide pour la clé '{}' — ignoré", logicalKey);
            return this;
        }
        dynamicToStable.put(dynamicUuid, stableUuid);
        stableToKey.put(stableUuid, logicalKey);
        log.debug("UUID normalisé : {} → {} ({})", dynamicUuid, stableUuid, logicalKey);
        return this;
    }

    /**
     * Surcharge sans clé logique, pour les UUID qui n'ont pas besoin d'apparaître dans
     * {@code aliases.json} (ex. configFile).
     */
    public CypressFixtureWriter withUuid(String dynamicUuid, String stableUuid) {
        if (dynamicUuid == null || dynamicUuid.isBlank()) {
            return this;
        }
        dynamicToStable.put(dynamicUuid, stableUuid);
        return this;
    }

    // ── Écriture ──────────────────────────────────────────────────────────

    /**
     * Normalise le contenu (remplacement des UUID), crée les répertoires parents si nécessaire,
     * puis écrit le fichier au chemin {@code basedir/<relativePath>}.
     *
     * <p>L'écriture se déroule en <strong>trois phases</strong> :
     * <ol>
     *   <li><strong>Remplacement des UUID connus</strong> : tous les UUID déjà dans
     *       {@code dynamicToStable} (utilisateurs, application, rows de fichiers précédents)
     *       sont remplacés par leur UUID stable.</li>
     *   <li><strong>Canonicalisation</strong> : tri des tableaux et des clés d'objets JSON
     *       pour produire un JSON déterministe indépendamment de l'ordre DB.</li>
     *   <li><strong>Auto-assignation</strong> : les UUID dynamiques résiduels (rowId du fichier
     *       courant) sont remplacés par des UUID stables séquentiels. L'ordre canonique garantit
     *       que le même UUID dynamique reçoit toujours le même numéro de séquence entre les runs,
     *       car l'exploration du JSON canonique est déterministe.</li>
     * </ol>
     *
     * @param relativePath chemin relatif du fichier cible
     * @param content      contenu brut de la réponse API
     */
    public void write(String relativePath, String content) throws IOException {
        // Phase 1 : remplacer les UUID déjà connus (user, app, rows des fichiers précédents)
        String phase1 = applyKnownReplacements(content);
        // Phase 2 : canonicaliser (tri tableaux + clés d'objets)
        String phase2 = canonicalize(phase1);
        // Phase 2b : normaliser les timestamps volatils (creationDate, updateDate, time…)
        String phase2b = normalizeTimestamps(phase2);
        // Phase 3 : auto-assigner les UUID dynamiques résiduels dans l'ordre canonique
        String phase3 = applyAutoAssignment(phase2b);
        Path target = baseDir.resolve(relativePath);
        Files.createDirectories(target.getParent());
        Files.writeString(target, phase3,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        log.info("Fixture générée : {}", target.toAbsolutePath());
    }

    /**
     * Écrit un fichier JSON {@code aliases.json} qui mappe les UUID <em>stables</em>
     * (valeurs constantes, committables) vers les UUID <em>dynamiques</em> du run courant.
     *
     * <p>Ce fichier peut être chargé dans Cypress avec {@code cy.fixture('…/aliases')} pour
     * construire dynamiquement les URL d'interception ({@code cy.intercept}) sans dépendre
     * d'UUID codés en dur.
     *
     * @param relativePath chemin relatif, ex.
     *                     {@code ui/cypress/fixtures/applications/ore/aliases.json}
     */
    public void writeAliases(String relativePath) throws IOException {
        // stableUuid → { key: logicalKey, dynamicUuid: "..." }
        Map<String, Object> aliases = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : stableToKey.entrySet()) {
            String stable  = e.getKey();
            String key     = e.getValue();
            String dynamic = dynamicToStable.entrySet().stream()
                    .filter(m -> m.getValue().equals(stable))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
            Map<String, String> entry = new LinkedHashMap<>();
            entry.put("key",         key);
            entry.put("stableUuid",  stable);
            entry.put("dynamicUuid", dynamic);
            aliases.put(key, entry);
        }
        write(relativePath, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(aliases));
    }

    // ── Utilitaires ───────────────────────────────────────────────────────

    // ── Normalisation ─────────────────────────────────────────────────────

    /**
     * Phase 2b : remplace tous les timestamps ISO-8601 du run courant ({@code creationDate},
     * {@code updateDate}, champ {@code time} du flux réactif…) par une valeur stable
     * {@code 1970-01-01T00:00:00.000000}, rendant les fixtures indépendantes de l'instant
     * d'exécution du test.
     */
    private String normalizeTimestamps(String content) {
        return TIMESTAMP_PATTERN.matcher(content).replaceAll("1970-01-01T00:00:00.000000");
    }

    /**
     * Phase 1 : remplace tous les UUID déjà connus dans {@code dynamicToStable}
     * (mappings explicites + auto-assignations des fichiers précédents).
     */
    private String applyKnownReplacements(String content) {
        String result = content;
        for (Map.Entry<String, String> entry : dynamicToStable.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Phase 3 : scanne le JSON canonique pour y trouver des UUID dynamiques résiduels
     * et leur attribuer séquentiellement des UUID stables.
     *
     * <p>Grâce au tri canonique préalable, le même UUID dynamique (ex. rowId d'un site)
     * apparaît toujours à la même position relative et reçoit donc toujours le même
     * numéro de séquence entre deux runs.
     */
    private String applyAutoAssignment(String content) {
        Set<String> stableUuids = new HashSet<>(dynamicToStable.values());
        Matcher matcher = UUID_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String uuid = matcher.group().toLowerCase(Locale.ROOT);
            final String replacement;
            if (stableUuids.contains(uuid)) {
                replacement = uuid;
            } else {
                replacement = dynamicToStable.computeIfAbsent(uuid, k -> {
                    rowUuidCounter++;
                    return String.format("c0000000-0000-0000-0000-%012d", rowUuidCounter);
                });
                stableUuids.add(replacement);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * @deprecated Utiliser {@link #write(String, String)} qui applique les trois phases
     *             (remplacement, canonicalisation, auto-assignation) dans le bon ordre.
     *             Conservé pour les tests unitaires existants.
     */
    @Deprecated
    String applyNormalization(String content) {
        return applyAutoAssignment(applyKnownReplacements(content));
    }

    /** Retourne le répertoire de base utilisé par ce writer. */
    public Path getBaseDir() {
        return baseDir;
    }

    /** Retourne une vue non modifiable de la map UUID dynamique → stable. */
    public Map<String, String> getDynamicToStableMap() {
        return java.util.Collections.unmodifiableMap(dynamicToStable);
    }

    // ── Canonicalisation JSON ─────────────────────────────────────────────

    /**
     * Canonicalise le JSON en triant récursivement tous les tableaux par la représentation
     * textuelle de leurs éléments.
     *
     * <p>Cela élimine toute non-déterminisme d'ordre provenant de collections Java non triées
     * ({@code HashSet}, {@code LinkedHashSet}) ou de requêtes PostgreSQL sans {@code ORDER BY}.
     * Les tableaux qui représentent des séquences ordonnées significatives (ex. clé naturelle
     * composite) ne sont <em>pas</em> affectés car leurs éléments sont des chaînes scalaires
     * dont le tri est déjà cohérent avec l'ordre d'insertion.
     *
     * @param json JSON après normalisation des UUID
     * @return JSON canonique avec tableaux triés et clés d'objets triées (via Jackson)
     */
    private String canonicalize(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        sortDeep(root);
        return objectMapper.writeValueAsString(root);
    }

    /**
     * Trie récursivement :
     * <ul>
     *   <li>les <strong>clés des objets</strong> JSON par ordre alphabétique ;</li>
     *   <li>les <strong>tableaux</strong> JSON par la représentation textuelle
     *       <em>canonique</em> (post-récursion) de leurs éléments.</li>
     * </ul>
     *
     * <p><strong>Important :</strong> la récursion se fait <em>avant</em> le tri du tableau
     * parent, afin que la clé de comparaison reflète la forme canonique complète de chaque
     * élément. Grâce à cela, les lignes dont la différence se situe tôt alphabétiquement
     * (ex. {@code hierarchicalKey}) sont triées de façon déterministe même si des champs
     * plus tardifs ({@code rowId}) contiennent encore des UUID dynamiques.
     */
    private void sortDeep(JsonNode node) {
        if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            List<JsonNode> elements = new ArrayList<>();
            arr.forEach(elements::add);
            // 1. Récursion dans chaque élément AVANT la comparaison du tableau
            elements.forEach(this::sortDeep);
            // 2. Tri par représentation canonique (déterministe)
            elements.sort(Comparator.comparing(JsonNode::toString));
            arr.removeAll();
            elements.forEach(arr::add);
        } else if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            List<String> keys = new ArrayList<>();
            obj.fieldNames().forEachRemaining(keys::add);
            // 1. Récursion dans les valeurs AVANT le tri des clés
            keys.forEach(k -> sortDeep(obj.get(k)));
            // 2. Tri alphabétique des clés et réinsertion
            Collections.sort(keys);
            Map<String, JsonNode> sorted = new LinkedHashMap<>();
            keys.forEach(k -> sorted.put(k, obj.get(k)));
            obj.removeAll();
            sorted.forEach(obj::set);
        }
    }

    // ── No-op writer (propriété cypress.fixtures.base.dir absente) ────────

    /**
     * Writer no-op retourné par {@link #defaultWriter()} quand la propriété système
     * {@value #BASE_DIR_PROPERTY} n'est pas définie.
     * Toutes les opérations d'écriture sont silencieusement ignorées.
     */
    private static final class NoOpCypressFixtureWriter extends CypressFixtureWriter {

        NoOpCypressFixtureWriter() {
            super(Paths.get(System.getProperty("java.io.tmpdir")));
        }

        @Override
        public void write(String relativePath, String content) {
            log.debug("NoOpCypressFixtureWriter.write ignoré (propriété {} non définie) : {}",
                    BASE_DIR_PROPERTY, relativePath);
        }

        @Override
        public void writeAliases(String relativePath) {
            log.debug("NoOpCypressFixtureWriter.writeAliases ignoré (propriété {} non définie) : {}",
                    BASE_DIR_PROPERTY, relativePath);
        }
    }
}