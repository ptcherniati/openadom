package fr.inra.oresing.persistence;

import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;

/**
 * Utilitaire d'aide à la construction d'identifiants SQL PostgreSQL respectant
 * la limite {@code NAMEDATALEN = 64} ( soit 63 octets utiles pour un nom ).
 *
 * <p>Au-delà de cette limite, PostgreSQL <b>tronque silencieusement</b> les
 * identifiants au moment du parsing. Sur des datatypes ACBB longs ( ex.
 * {@code t_soil_analysis_sana_complete_long_*} ), deux datatypes différents
 * peuvent produire des index dont les 63 premiers octets sont identiques :
 * le {@code CREATE INDEX IF NOT EXISTS} devient un no-op sur le second et
 * l'un des datatypes se retrouve sans index ( cf. AUDIT 2026-05-08 ).</p>
 *
 * <p>La méthode {@link #truncateSafe(String, int)} évite ce piège en
 * conservant un préfixe lisible et en y ajoutant un suffixe court calculé
 * à partir d'un hash CRC32 de l'identifiant complet, garantissant l'unicité
 * tout en restant déterministe ( même entrée → même sortie, sans état ).</p>
 *
 * <p>L'algorithme :
 * <ol>
 *   <li>Si {@code identifier.length() + reservedSuffixLength <= 63} : renvoyé tel quel.</li>
 *   <li>Sinon : {@code prefix(N) + '_' + crc32_hex(8) } où {@code N = 63 - reservedSuffixLength - 9}.
 *       9 octets sont réservés pour le séparateur {@code _} ( 1 ) et les
 *       8 caractères hexadécimaux du hash.</li>
 * </ol>
 * </p>
 *
 * <p>Volontairement {@code static-only} ; aucune dépendance Spring : utilisable
 * depuis un {@code record} immuable comme {@code AuthorizationIndex}.</p>
 */
public final class PgIdentifier {

    /** Limite réelle d'un identifiant ASCII PostgreSQL ( {@code NAMEDATALEN - 1 = 63} octets ). */
    public static final int MAX_IDENTIFIER_LENGTH = 63;

    /** Longueur du suffixe de hash en hexadécimal ( CRC32 -> 8 chars ). */
    static final int HASH_SUFFIX_LENGTH = 8;

    /** Octets consommés par le séparateur ( 1 ) + le hash ( 8 ) = 9. */
    static final int HASH_OVERHEAD = 1 + HASH_SUFFIX_LENGTH;

    private PgIdentifier() {
        // classe utilitaire ( pas d'instance )
    }

    /**
     * Tronque un identifiant pour qu'il tienne dans {@link #MAX_IDENTIFIER_LENGTH}
     * tout en réservant {@code reservedSuffixLength} octets pour un suffixe
     * concaténé en aval ( ex. {@code "_refvalues_index"} ajouté plus tard ).
     *
     * <p>Si l'identifiant tient déjà : renvoyé tel quel.<br>
     * Sinon : préfixe lisible + {@code _} + 8 chars hex du CRC32 de
     * l'identifiant complet ( unicité garantie même après troncature ).</p>
     *
     * @param identifier            identifiant brut ( par exemple
     *                              {@code "authorization_<dataname>_index"} )
     * @param reservedSuffixLength  nombre d'octets à réserver pour un suffixe
     *                              ajouté ultérieurement par l'appelant
     *                              ( ex. 16 pour {@code "_refvalues_index"} ) ;
     *                              doit être {@code >= 0}
     * @return un identifiant garanti {@code <= MAX_IDENTIFIER_LENGTH - reservedSuffixLength}
     * @throws IllegalArgumentException si {@code identifier} est {@code null}
     *                                  ou si la marge est insuffisante pour
     *                                  inclure le préfixe minimal + le hash
     */
    public static String truncateSafe(final String identifier, final int reservedSuffixLength) {
        if (identifier == null) {
            throw new IllegalArgumentException("identifier must not be null");
        }
        if (reservedSuffixLength < 0) {
            throw new IllegalArgumentException("reservedSuffixLength must be >= 0");
        }
        final int budget = MAX_IDENTIFIER_LENGTH - reservedSuffixLength;
        if (budget <= 0) {
            throw new IllegalArgumentException(
                    "reservedSuffixLength " + reservedSuffixLength
                    + " leaves no room for the identifier ( max " + MAX_IDENTIFIER_LENGTH + " )");
        }
        if (identifier.length() <= budget) {
            return identifier;
        }
        final int prefixLength = budget - HASH_OVERHEAD;
        if (prefixLength < 1) {
            throw new IllegalArgumentException(
                    "reservedSuffixLength " + reservedSuffixLength
                    + " leaves no room for the truncated prefix + hash suffix ( "
                    + HASH_OVERHEAD + " bytes )");
        }
        final String prefix = identifier.substring(0, prefixLength);
        final String hash = crc32Hex(identifier);
        return prefix + "_" + hash;
    }

    /**
     * Renvoie {@code true} si l'identifiant brut dépasse la limite
     * PostgreSQL et serait donc tronqué silencieusement par le serveur.
     */
    public static boolean wouldBeTruncated(final String identifier) {
        return identifier != null && identifier.length() > MAX_IDENTIFIER_LENGTH;
    }

    /**
     * Hash CRC32 de la chaîne complète ( UTF-8 ), formaté sur exactement
     * 8 caractères hexadécimaux minuscules.
     */
    private static String crc32Hex(final String input) {
        final CRC32 crc = new CRC32();
        crc.update(input.getBytes(StandardCharsets.UTF_8));
        return String.format("%08x", crc.getValue());
    }
}
