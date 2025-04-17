package fr.inra.oresing.domain.application.configuration;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import lombok.Value;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Représente une donnée correspondant à une valeur de type <code>ltree</code>.
 * <p> <p>
 * Un ltree correspond à une séquence de labels séparés par des points. Les labels sont
 * contraingnants en terme de syntaxe et cette classe gère l'échappement<a href="<a">href=".
 * ">* <p> <p>
 * https://www.postgresql.</a>org/docs/cu</a>rrent/ltree.html
 */
@Value
public class Ltree implements Comparable<Ltree> {
    /**
     * Déliminateur entre les différents niveaux d'un ltree postgresql.
     */
    public static final String SEPARATOR = ".";
    public static final String NULL_KEY = "NULL_KEY";
    private static final Pattern LABEL_INVALID_CHARACTERS_REGEX = Pattern.compile("[^a-zA-Z0-9_]");
    private static final Pattern VALID_LABEL_REGEX = Pattern.compile("[a-zA-Z0-9_]+");
    private static final Ltree EMPTY_LTREE_SINGLETON = new Ltree("");
    /*public static Set<String> KNOWN_SYMBOL_CODES = IntStream.range(Character.MIN_CODE_POINT, Character.MAX_CODE_POINT)
            .filter(Character::isValidCodePoint)
            .filter(Character::isDefined)
            .mapToObj(i -> Character.getName(i).replaceAll("[ -]", "")).collect(Collectors.toCollection(HashSet::new));*/
    public static Set<String> KNOWN_SYMBOL_CODES = IntStream.range(0, 0x3FF)
            .filter(Character::isValidCodePoint)
            .filter(Character::isDefined)
            .mapToObj(i -> Character.getName(i).replaceAll("[ -]", "")).collect(Collectors.toCollection(HashSet::new));
    String sql;

    private Ltree(final String sql) {
        super();
        this.sql = sql;
    }

    public static Ltree fromSqlWithoutCheck(String text) {
        return new Ltree(text);
    }

    public Ltree last() {
        return Ltree.fromSql(getSql().replaceAll(".*\\.", ""));
    }

    /**
     * Construire à partir d'un ltree tel qu'il a pu existé en base (donc déjà échappé et syntaxiquement correct)
     */
    public static Ltree fromSql(final String sql) {
        checkSyntax(sql);
        return new Ltree(sql);
    }

    /**
     * Constuire en concaténant deux ltree pour en former un
     */
    public static Ltree join(final Ltree prefix, final Ltree suffix) {
        return fromSql(prefix.sql + SEPARATOR + suffix.sql);
    }

    public static Ltree fromUnescapedString(final String labelToEscape) {
        final String escaped = escapeToLabel(labelToEscape);
        return fromSql(escaped);
    }

    public static String escapeToLabel(String key, Set<String> knownSpecialCharacters) {
        if (VALID_LABEL_REGEX.asMatchPredicate().test(key) && isEncodedString(key, knownSpecialCharacters)) {
            return key;
        }
        return extracttolabelFromStringWithSpecialCharacters(key);
    }


    private static String extracttolabelFromStringWithSpecialCharacters(String key) {
        final String lowerCased = key.replace(Ltree.NULL_KEY, "____").toLowerCase();
        final String withAccentsStripped = StringUtils.stripAccents(lowerCased);
        final String withoutSpace = StringUtils.replace(withAccentsStripped, " ", "_");
        final String toEscape = StringUtils.remove(withoutSpace, "-");
        final String escaped = toEscape.chars()
                .mapToObj(x -> (char) x)
                .map(Ltree::escapeSymbolFromKeyComponent)
                .collect(Collectors.joining());
        checkLabelSyntax(escaped);
        return escaped
                .replaceAll("________", "__NULL_KEY__")
                .replaceAll("^______", "NULL_KEY__")
                .replaceAll("______$", "__NULL_KEY");
    }

    /**
     * Échapper une chaîne pour former un label.
     */
    public static String escapeToLabel(final String key) {
        if (VALID_LABEL_REGEX.asMatchPredicate().test(key) && isEncodedString(key)) {
            return key;
        }
        return extracttolabelFromStringWithSpecialCharacters(key);
    }

    public static void checkLabelSyntax(final String label) {
        Preconditions.checkState(label.length() <= 256, ExceptionMessage.TOO_LONG_LABEL.toMessage());
        Preconditions.checkState(!label.isEmpty(), ExceptionMessage.NULL_LABEL.toMessage());
        Preconditions.checkState(VALID_LABEL_REGEX.matcher(label).matches(), ExceptionMessage.INAPPROPRIATE_LABEL.toMessage(), label);
    }

    private static String escapeSymbolFromKeyComponent(final Character aChar) {
        final String escapedChar;
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

    public static boolean isEncodedString(String label) {
        return KNOWN_SYMBOL_CODES.stream()
                .parallel()
                .anyMatch(label::contains);
    }

    public static boolean isEncodedString(String label, Set<String> knownSpecialCharacters) {
        if (knownSpecialCharacters.isEmpty() || label.matches("^[a-z0-9_]*$")) {
            return false;
        }
        return knownSpecialCharacters.stream()
                .parallel()
                .anyMatch(label::contains);
    }

    /**
     * D'après la documentation PostgreSQL sur ltree
     *
     * <blockquote>
     * A label is a sequence of alphanumeric characters and underscores (for example, in C locale the characters A-Za-z0-9_ are allowed).
     * </blockquote>
     */
    private static boolean characterCanBeUsedInLabel(final Character aChar) {
        return CharUtils.isAsciiAlphanumeric(aChar) || '_' == aChar;
    }

    public static void checkSyntax(final String sql) {
        Splitter.on(SEPARATOR).split(sql).forEach(Ltree::checkLabelSyntax);
    }

    public static Ltree empty() {
        return EMPTY_LTREE_SINGLETON;
    }

    @Override
    public String toString() {
        return sql;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final Ltree ltree = (Ltree) o;
        return Objects.equals(sql, ltree.sql);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sql);
    }

    public boolean isAncestorOf(Ltree other) {
        return other.getSql().startsWith(this.sql + ".");
    }

    public boolean isAncestorOfAny(List<Ltree> others) {
        return others.stream().anyMatch(this::isAncestorOf);
    }

    @Override
    public int compareTo(Ltree o) {
        return getSql().compareTo(o.getSql());
    }
}