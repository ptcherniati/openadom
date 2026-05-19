package fr.inra.oresing.rest.usecases.storage.versioning;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Calcule un hash deterministe ( SHA-256 ) du sous-ensemble de la
 * configuration applicative pertinent pour un datatype donne .
 *
 * <p>Utilise par {@link PublishLifecycleService} comme **safety check**
 * pour decider si un republish peut emprunter un eventuel chemin
 * " lite " ( bypass validators ) ou FAST ( COPY processed_data ) :
 *
 * <ul>
 *   <li>au moment du 1er upload d'un fichier , le hash courant de la
 *       config du datatype est persiste dans {@code binaryfile.params.configHash} ;</li>
 *   <li>au republish , on recalcule le hash et on compare ;</li>
 *   <li>hash identique = la config n'a pas change , le data deja
 *       valide reste valide → lite / FAST eligible ;</li>
 *   <li>hash different = la config a evolue ( colonnes ajoutees ,
 *       types changes , validators modifies ) → FULL path obligatoire
 *       pour re-valider .</li>
 * </ul>
 *
 * <p>Le hash couvre uniquement le {@link StandardDataDescription} du
 * datatype concerne ( pas toute la configuration applicative ) : un
 * changement sur un AUTRE datatype ne doit pas invalider le hash
 * du datatype courant .
 *
 * <h2>Determinisme cross-JVM</h2>
 *
 * <p>Le hash doit etre <b>strictement deterministe</b> entre deux runs
 * JVM successifs ( redeploiement backend , restart container ) pour la
 * meme configuration logique . Les pieges :
 * <ul>
 *   <li>{@code Set<X>} ( ex {@code Set<Tag>} dans {@link StandardDataDescription} )
 *       backe par un {@code HashSet} a un iteration order qui depend de
 *       la capacite initiale et du hashCode bucket distribution , pas
 *       garanti stable entre JVMs malgre des hashCode stables ;</li>
 *   <li>{@code Map<K,V>} ( deja couvert via
 *       {@link SerializationFeature#ORDER_MAP_ENTRIES_BY_KEYS} ) ;</li>
 *   <li>{@code List<X>} construit par streaming d'un {@code Map.entrySet()}
 *       herite de l'ordre de la map source , potentiellement instable
 *       si la map sous-jacente est {@code HashMap} .</li>
 * </ul>
 *
 * <p>Solution : on serialise d'abord en arbre {@link JsonNode} via Jackson ,
 * puis on emet une representation <b>canonique</b> ( cles d'objets triees +
 * elements d'arrays tries lexicographiquement ) avant SHA-256 . Cout O(n log n)
 * sur la taille de l'arbre , negligeable devant SHA-256 lui-meme .
 *
 * <p>Trade-off : trier les arrays rend le hash insensible a l'ordre des
 * Lists ( ex {@code List<Depends>} , {@code List<MigrationDescription>} ) .
 * Pour le cas d'usage FAST path / lite ( "config a-t-elle change semantiquement" ) ,
 * un reorder pur sans modification de contenu produit le meme hash , ce qui
 * est ACCEPTABLE : le contenu deja valide reste valide quel que soit l'ordre
 * de declaration .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class ConfigHashService {

    /**
     * ObjectMapper configure pour serialiser le {@link StandardDataDescription}
     * du datatype :
     * <ul>
     *   <li>JSR310 module pour {@code LocalDateTime} ( utilise par
     *       {@code DateChecker.min/max} ) - sinon
     *       {@code InvalidDefinitionException} -&gt; computeHash empty
     *       -&gt; lite-v1 force FULL toujours -&gt; OOM cross-row map ;</li>
     *   <li>Pas de FAIL_ON_EMPTY_BEANS ( certaines composantes peuvent etre
     *       vides apres construction ) ;</li>
     *   <li>ORDER_MAP_ENTRIES_BY_KEYS pour determinisme du hash entre
     *       chargements YAML successifs ( si Map est HashMap ailleurs ) .</li>
     * </ul>
     */
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /**
     * @return hash hex SHA-256 du {@link StandardDataDescription}
     *         du datatype , ou {@link Optional#empty} si le datatype
     *         est introuvable ou si la serialisation echoue .
     */
    public Optional<String> computeHash(Application application, String dataName) {
        if (application == null || dataName == null) return Optional.empty();
        Configuration config = application.getConfiguration();
        if (config == null) return Optional.empty();
        StandardDataDescription desc = Optional.ofNullable(config.dataDescription())
                .map(map -> map.get(dataName))
                .orElse(null);
        if (desc == null) return Optional.empty();
        try {
            JsonNode tree = mapper.valueToTree(desc);
            byte[] bytes = writeCanonical(tree);
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha.digest(bytes);
            return Optional.of(HexFormat.of().formatHex(digest));
        } catch (NoSuchAlgorithmException | RuntimeException | IOException ex) {
            log.warn("ConfigHash compute failed for app={} dataName={} : {} : {}",
                    application.getName(), dataName, ex.getClass().getSimpleName(), ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @return {@code true} si le hash courant matche le hash stocke
     *         ( config inchangee depuis l'upload ) ; {@code false}
     *         sinon ou si l'un des deux est absent ( decision
     *         conservative : on force FULL path ) .
     */
    public boolean configUnchangedSinceUpload(Application application, String dataName, String storedHash) {
        if (storedHash == null || storedHash.isBlank()) return false;
        return computeHash(application, dataName)
                .map(current -> current.equals(storedHash))
                .orElse(false);
    }

    /**
     * Emet une representation canonique UTF-8 de l'arbre JSON :
     * <ul>
     *   <li>cles d'objets triees lexicographiquement ;</li>
     *   <li>elements d'arrays tries lexicographiquement par leur forme
     *       canonique recursive .</li>
     * </ul>
     *
     * <p>Garantit qu'une meme configuration logique produit le meme byte
     * stream quel que soit l'ordre d'iteration des Sets / Maps sous-jacents .
     *
     * <p>Visibility package-private pour faciliter les tests unitaires .
     */
    byte[] writeCanonical(JsonNode root) throws IOException {
        StringWriter sw = new StringWriter();
        try (JsonGenerator gen = mapper.getFactory().createGenerator(sw)) {
            writeCanonicalNode(root, gen);
        }
        return sw.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void writeCanonicalNode(JsonNode node, JsonGenerator gen) throws IOException {
        if (node == null || node.isNull()) {
            gen.writeNull();
            return;
        }
        if (node.isObject()) {
            // Cles triees ( TreeSet garantit l'ordre lexicographique stable ) .
            TreeSet<String> sortedKeys = new TreeSet<>();
            node.fieldNames().forEachRemaining(sortedKeys::add);
            gen.writeStartObject();
            for (String key : sortedKeys) {
                gen.writeFieldName(key);
                writeCanonicalNode(node.get(key), gen);
            }
            gen.writeEndObject();
            return;
        }
        if (node.isArray()) {
            // Pour chaque enfant , on calcule sa forme canonique en string ,
            // puis on trie ces strings lexicographiquement avant emission .
            // Coute O(n*size) mais reste negligeable devant SHA-256 lui-meme
            // sur des configurations realistes ( <100 KB serialise ) .
            List<String> childCanonicals = new ArrayList<>(node.size());
            for (JsonNode child : node) {
                StringWriter csw = new StringWriter();
                try (JsonGenerator cgen = mapper.getFactory().createGenerator(csw)) {
                    writeCanonicalNode(child, cgen);
                }
                childCanonicals.add(csw.toString());
            }
            Collections.sort(childCanonicals);
            gen.writeStartArray();
            for (String c : childCanonicals) {
                gen.writeRawValue(c);
            }
            gen.writeEndArray();
            return;
        }
        // Primitif ( string , number , boolean ) : ecrit tel quel via writeTree
        // qui re-utilise le JsonGenerator courant proprement ( pas de
        // double-encoding ) .
        gen.writeTree(node);
    }
}
