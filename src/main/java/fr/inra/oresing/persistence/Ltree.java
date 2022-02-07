package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import lombok.Value;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;
import java.util.stream.Collectors;

@Value
public class Ltree {

    /**
     * Déliminateur entre les différents niveaux d'un ltree postgresql.
     * <p>
     * https://www.postgresql.org/docs/current/ltree.html
     */
    public static final String SEPARATOR = ".";

    String sql;

    private Ltree(String sql) {
        this.sql = sql;
    }

    public static Ltree fromSql(String sql) {
        checkSyntax(sql);
        return new Ltree(sql);
    }

    public static String escapeLabel(String key) {
        String lowerCased = key.toLowerCase();
        String withAccentsStripped = StringUtils.stripAccents(lowerCased);
        String toEscape = StringUtils.replace(withAccentsStripped, " ", "_");
        String escaped = toEscape.chars()
                .mapToObj(x -> (char) x)
                .map(Ltree::escapeSymbolFromKeyComponent)
                .collect(Collectors.joining());
        checkLabelSyntax(escaped);
        return escaped;
    }

    public static void checkLabelSyntax(String keyComponent) {
        Preconditions.checkState(keyComponent.length() <= 256, "Un label dans un ltree ne peut pas être plus long que 256 caractères à cause de PG");
        Preconditions.checkState(!keyComponent.isEmpty(), "La clé naturelle ne peut être vide. vérifier le nom des colonnes.");
        Preconditions.checkState(keyComponent.matches("[a-zA-Z0-9_]+"), keyComponent + " n'est pas un élément valide pour une clé naturelle");
    }

    private static String escapeSymbolFromKeyComponent(Character aChar) {
        String escapedChar;
        if (characterCanBeUsedInLabel(aChar)) {
            escapedChar = CharUtils.toString(aChar);
        } else {
            escapedChar = RegExUtils.replaceAll(
                    Character.getName(aChar),
                    "[^a-zA-Z0-9_]",
                    ""
            );
        }
        return escapedChar;
    }

    /**
     * D'après la documentation PostgreSQL sur ltree
     *
     * <blockquote>
     *     A label is a sequence of alphanumeric characters and underscores (for example, in C locale the characters A-Za-z0-9_ are allowed). Labels must be less than 256 characters long.
     * </blockquote>
     */
    private static boolean characterCanBeUsedInLabel(Character aChar) {
        return CharUtils.isAsciiAlphanumeric(aChar) || '_' == aChar;
    }

    public static void checkSyntax(String sql) {
        Splitter.on(SEPARATOR).split(sql).forEach(Ltree::checkLabelSyntax);
    }

    public static Ltree join(String prefix, String suffix) {
        checkSyntax(prefix);
        checkSyntax(suffix);
        return fromSql(prefix + SEPARATOR + suffix);
    }

    public static Ltree join(Ltree prefix, Ltree suffix) {
        return join(prefix.getSql(), suffix.getSql());
    }

    public static Ltree parseLabel(String labelToEscape) {
        String escaped = escapeLabel(labelToEscape);
        return fromSql(escaped);
    }

    public static Ltree toLabel(UUID uuid) {
        String escaped = escapeLabel(StringUtils.remove(uuid.toString(), "-"));
        return fromSql(escaped);
    }
}
