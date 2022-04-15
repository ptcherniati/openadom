package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import lombok.Value;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Représente une donnée correspondant à une valeur de type <code>ltree</code>.
 *
 * Un ltree correspond à une séquence de labels séparés par des points. Les labels sont
 * contraingnants en terme de syntaxe et cette classe gère l'échappement.
 *
 * https://www.postgresql.org/docs/current/ltree.html
 */
@Value
public class Ltree {

    /**
     * Déliminateur entre les différents niveaux d'un ltree postgresql.
     */
    public static final String SEPARATOR = ".";

    private static final Pattern LABEL_INVALID_CHARACTERS_REGEX = Pattern.compile("[^a-zA-Z0-9_]");

    private static final Pattern VALID_LABEL_REGEX = Pattern.compile("[a-zA-Z0-9_]+");

    private static final Ltree EMPTY_LTREE_SINGLETON = new Ltree("");

    String sql;

    private Ltree(String sql) {
        this.sql = sql;
    }

    /**
     * Construire à partir d'un ltree tel qu'il a pu existé en base (donc déjà échappé et syntaxiquement correct)
     */
    public static Ltree fromSql(String sql) {
        checkSyntax(sql);
        return new Ltree(sql);
    }

    /**
     * Constuire un label à partir d'un UUID
     */
    public static Ltree fromUuid(UUID uuid) {
        String escaped = escapeToLabel(StringUtils.remove(uuid.toString(), "-"));
        return fromSql(escaped);
    }

    /**
     * Constuire en concaténant deux ltree pour en former un
     */
    public static Ltree join(Ltree prefix, Ltree suffix) {
        return fromSql(prefix.getSql() + SEPARATOR + suffix.getSql());
    }

    public static Ltree fromUnescapedString(String labelToEscape) {
        String escaped = escapeToLabel(labelToEscape);
        return fromSql(escaped);
    }

    /**
     * Échapper une chaîne pour former un label.
     */
    public static String escapeToLabel(String key) {
        String lowerCased = key.toLowerCase();
        String withAccentsStripped = StringUtils.stripAccents(lowerCased);
        String withoutSpace = StringUtils.replace(withAccentsStripped, " ", "_");
        String toEscape = StringUtils.remove(withoutSpace, "-");
        String escaped = toEscape.chars()
                .mapToObj(x -> (char) x)
                .map(Ltree::escapeSymbolFromKeyComponent)
                .collect(Collectors.joining());
        checkLabelSyntax(escaped);
        return escaped;
    }

    public static void checkLabelSyntax(String label) {
        Preconditions.checkState(label.length() <= 256, "Un label dans un ltree ne peut pas être plus long que 256 caractères");
        Preconditions.checkState(!label.isEmpty(), "Un label ne peut être vide");
        Preconditions.checkState(VALID_LABEL_REGEX.matcher(label).matches(), label + " contient des caractères invalides");
    }

    private static String escapeSymbolFromKeyComponent(Character aChar) {
        String escapedChar;
        if (characterCanBeUsedInLabel(aChar)) {
            escapedChar = CharUtils.toString(aChar);
        } else {
            escapedChar = RegExUtils.removeAll(
                    Character.getName(aChar),
                    LABEL_INVALID_CHARACTERS_REGEX
            );
        }
        return escapedChar;
    }

    /**
     * D'après la documentation PostgreSQL sur ltree
     *
     * <blockquote>
     *     A label is a sequence of alphanumeric characters and underscores (for example, in C locale the characters A-Za-z0-9_ are allowed).
     * </blockquote>
     */
    private static boolean characterCanBeUsedInLabel(Character aChar) {
        return CharUtils.isAsciiAlphanumeric(aChar) || '_' == aChar;
    }

    public static void checkSyntax(String sql) {
        Splitter.on(SEPARATOR).split(sql).forEach(Ltree::checkLabelSyntax);
    }

    public static Ltree empty() {
        return EMPTY_LTREE_SINGLETON;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
