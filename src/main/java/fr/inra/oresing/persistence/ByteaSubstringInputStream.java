package fr.inra.oresing.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * {@link InputStream} qui lit un blob Postgres {@code bytea} via des
 * appels {@code SUBSTRING(column FROM offset FOR length)} successifs ,
 * sans jamais charger l'integralite du blob en heap .
 *
 * <p><b>Pourquoi :</b> le driver Postgres JDBC fetche tout {@code bytea}
 * en memoire avant de retourner un {@code ResultSet} . Pour un fichier
 * 200 MB stocke en bytea , Spring's {@code DefaultLobHandler
 * .getBlobAsBinaryStream} retourne en realite un
 * {@link java.io.ByteArrayInputStream} qui wrappe les 200 MB deja
 * fetches . Cette classe contourne le probleme : chaque appel
 * {@link #read(byte[], int, int)} declenche un SUBSTRING server-side
 * d'au plus {@link #chunkBytes} octets , libere immediatement la
 * connexion , et conserve uniquement le buffer courant en heap .
 *
 * <p><b>Trade-offs :</b>
 * <ul>
 *   <li>+ Memoire heap = O(chunkBytes) ( typique 1 MB ) au lieu de
 *       O(totalSize) ;</li>
 *   <li>+ Compatible cascade {@code FileChunkSource} ( juste un InputStream ) ;</li>
 *   <li>+ Compatible avec les CSV parsers existants ;</li>
 *   <li>- Plus de round-trips DB ( N appels au lieu d'1 ) ; acceptable
 *       car bytea SUBSTRING est rapide ( index PK + offset ) ;</li>
 *   <li>- Pas thread-safe : un seul reader a la fois ( pas un
 *       probleme : cascade lit le stream sequentiellement ) .</li>
 * </ul>
 *
 * <p><b>Workaround temporaire</b> en attendant l'integration de
 * {@code Sources.bytea(...)} dans cascade ( cf
 * {@code CASCADE_IMPROVEMENTS.md} #A ) . Une fois cascade A livre ,
 * cette classe pourra etre supprimee et le publish pipeline pourra
 * utiliser directement la source cascade native .
 *
 * @author R.YAHIAOUI
 */
public class ByteaSubstringInputStream extends InputStream {

    /** Taille recommandee par chunk ( 1 MB ) : compromis memoire / latence . */
    public static final int DEFAULT_CHUNK_BYTES = 1024 * 1024;

    private final JdbcTemplate jdbcTemplate;
    private final String       selectSubstringSql;
    private final UUID         pkValue;
    private final int          chunkBytes;

    private byte[] currentBuffer;   // chunk courant en memoire
    private int    currentPos;      // position dans currentBuffer
    private long   absoluteOffset;  // position 1-based dans le bytea ( pour next SUBSTRING )
    private boolean eof;            // true quand le dernier chunk a renvoye 0 byte

    /**
     * @param jdbcTemplate   pool JDBC ( typiquement le main ) ; chaque chunk
     *                       acquiert une connection et la libere immediatement
     * @param schemaName     schema applicatif ( ex. " si_acbb " )
     * @param tableName      table contenant le bytea ( ex. " binaryfile " )
     * @param byteaColumn    nom de la colonne bytea ( ex. " filedata " )
     * @param pkColumn       colonne PK uuid ( ex. " id " )
     * @param pkValue        valeur PK
     * @param chunkBytes     taille max d'un chunk lu en 1 SUBSTRING ( recommande 1 MB ;
     *                       trop petit = N round-trips inutiles , trop gros = heap pic )
     */
    public ByteaSubstringInputStream(
            JdbcTemplate jdbcTemplate,
            String       schemaName,
            String       tableName,
            String       byteaColumn,
            String       pkColumn,
            UUID         pkValue,
            int          chunkBytes) {
        if (chunkBytes <= 0) {
            throw new IllegalArgumentException("chunkBytes must be > 0 , got " + chunkBytes);
        }
        this.jdbcTemplate       = jdbcTemplate;
        this.pkValue            = pkValue;
        this.chunkBytes         = chunkBytes;
        // Postgres SUBSTRING ( bytea FROM offset FOR length ) : offset 1-based .
        // ::uuid cast inline pour eviter setObject ambiguous mapping .
        // ::int CRITIQUE : Spring envoie absoluteOffset ( long ) en bigint ,
        // mais PG 18 n'a pas SUBSTRING(bytea, bigint, integer) -> cast int
        // explicite ( ok car blobs < 2^31 bytes = 2 GB ; au-dela on aurait
        // d'autres problemes avant ) .
        this.selectSubstringSql = String.format(
                "SELECT SUBSTRING(%s FROM ?::int FOR ?::int) FROM %s.%s WHERE %s = ?::uuid",
                byteaColumn, schemaName, tableName, pkColumn);
        this.absoluteOffset     = 1L;  // bytea SUBSTRING starts at 1
        this.eof                = false;
    }

    @Override
    public int read() throws IOException {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        return n <= 0 ? -1 : (one[0] & 0xff);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException();
        }
        if (len == 0) return 0;
        if (eof) return -1;

        // Recharge si buffer courant epuise
        if (currentBuffer == null || currentPos >= currentBuffer.length) {
            if (!loadNextChunk()) {
                return -1;
            }
        }
        int available = currentBuffer.length - currentPos;
        int toCopy    = Math.min(available, len);
        System.arraycopy(currentBuffer, currentPos, b, off, toCopy);
        currentPos += toCopy;
        return toCopy;
    }

    /**
     * Fetch le prochain chunk via SUBSTRING . Met a jour
     * {@link #currentBuffer} et {@link #absoluteOffset} . Retourne
     * {@code false} si EOF ( aucun octet renvoye ) .
     */
    private boolean loadNextChunk() {
        byte[] chunk = jdbcTemplate.queryForObject(
                selectSubstringSql,
                byte[].class,
                absoluteOffset, chunkBytes, pkValue.toString());
        if (chunk == null || chunk.length == 0) {
            eof          = true;
            currentBuffer = null;
            return false;
        }
        currentBuffer  = chunk;
        currentPos     = 0;
        absoluteOffset += chunk.length;
        return true;
    }

    @Override
    public void close() {
        currentBuffer  = null;
        eof            = true;
    }
}
