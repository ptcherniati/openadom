package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core.config")
class LtreeTest {
    @Test
    void assertThatAStringWithInvalidCharactersCanBeEncodedTwice() {
        String label = "TOTOé_°_%_>_²_؇_?";
        Ltree nk = Ltree.fromUnescapedString(label);
        // String encodedString = "totoe_DEGREESIGN_PERCENTSIGN_GREATERTHANSIGN_SUPERSCRIPTTWO_ARABICINDICFOURTHROOT_QUESTIONMARK";
        /*
         * Apache Commons Lang utilise maintenant Normalizer de Java (java.text.Normalizer) 
         * 👉 Anciennes versions (3.9, 3.10)
            stripAccents("²") laissait "²", générait "SUPERSCRIPTTWO"
           👉 Nouvelles versions (ex: 3.12.0, 3.13.0 et après)
            stripAccents("²") transforme "²" → "2" directement !
         */
        String encodedString = "totoe_DEGREESIGN_PERCENTSIGN_GREATERTHANSIGN_2_ARABICINDICFOURTHROOT_QUESTIONMARK";
        Ltree secondEncoding = Ltree.fromUnescapedString(encodedString);
        assertEquals(encodedString, nk.getSql());
        assertEquals(encodedString, secondEncoding.getSql());
    }

    @Test
    void parseLabel() {
        final String sql = Ltree.fromUnescapedString("composition <5%/µg").getSql();

        // Remarque :
        // Le caractère 'µ' (MICRO SIGN, U+00B5) est automatiquement transformé par Java
        // en 'μ' (GREEK SMALL LETTER MU, U+03BC) lors de la normalisation ( par exemple via Normalizer.normalize ou StringUtils.stripAccents ).
        // Cela est dû à une homogénéisation Unicode.
        // Cela génère "GREEKSMALLLETTERMU" au lieu de "MICROSIGN".

        // assertEquals("composition_LESSTHANSIGN5PERCENTSIGNSOLIDUSMICROSIGNg", sql);
        assertEquals("composition_LESSTHANSIGN5PERCENTSIGNSOLIDUSGREEKSMALLLETTERMUg", sql);
    }

    @ParameterizedTest(name = "{0} match an encodingString")
    @ValueSource(strings = {"%", ">", "$", "?", "&", "@", "°", "µ"})
    void testIsEcodedString(String aSign) {
        String aChar = Ltree.fromUnescapedString(aSign).getSql();
        assertTrue(Ltree.isEncodedString(aChar));
    }

    @ParameterizedTest(name = "{0} doesn't match an encodingString")
    @ValueSource(strings = {"_", "A", "2", "a", "²"})
    void testIsNotEncodedString(String aSign) {
        String aChar = Ltree.fromUnescapedString(aSign).getSql();
        assertFalse(Ltree.isEncodedString(aChar));
    }

    @Test
    void fromSqlWithoutCheck_acceptsAnything() {
        Ltree l = Ltree.fromSqlWithoutCheck("anything.with.dots");
        assertEquals("anything.with.dots", l.getSql());
    }

    @Test
    void fromSql_validMultiLevel_ok() {
        Ltree l = Ltree.fromSql("a.b.c");
        assertEquals("a.b.c", l.getSql());
    }

    @Test
    void fromSql_invalidLabel_throws() {
        assertThrows(IllegalStateException.class, () -> Ltree.fromSql("bad-label"));
    }

    @Test
    void fromSql_emptyLabel_throws() {
        assertThrows(IllegalStateException.class, () -> Ltree.fromSql(""));
    }

    @Test
    void checkLabelSyntax_tooLongRejected() {
        String huge = "a".repeat(257);
        assertThrows(IllegalStateException.class, () -> Ltree.checkLabelSyntax(huge));
    }

    @Test
    void checkLabelSyntax_invalidChars_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> Ltree.checkLabelSyntax("bad chars"));
    }

    @Test
    void join_concatenatesWithSeparator() {
        Ltree p = Ltree.fromSql("parent");
        Ltree c = Ltree.fromSql("child");
        assertEquals("parent.child", Ltree.join(p, c).getSql());
    }

    @Test
    void empty_returnsSingletonWithEmptySql() {
        assertSame(Ltree.empty(), Ltree.empty());
        assertEquals("", Ltree.empty().getSql());
    }

    @Test
    void fromJson_andToJson_roundTrip() {
        Ltree l = Ltree.fromJson("foo.bar");
        assertEquals("foo.bar", l.toJson());
    }

    @Test
    void last_returnsLastLabelOnly() {
        assertEquals("c", Ltree.fromSql("a.b.c").last().getSql());
        assertEquals("only", Ltree.fromSql("only").last().getSql());
    }

    @Test
    void toString_returnsSql() {
        assertEquals("a.b", Ltree.fromSql("a.b").toString());
    }

    @Test
    void equalsAndHashCode_consistent() {
        Ltree a1 = Ltree.fromSql("a.b");
        Ltree a2 = Ltree.fromSql("a.b");
        Ltree b = Ltree.fromSql("a.c");
        assertEquals(a1, a2);
        assertEquals(a1.hashCode(), a2.hashCode());
        assertNotEquals(a1, b);
        assertNotEquals(null, a1);
        assertNotEquals("a.b", a1);
        assertEquals(a1, a1);
    }

    @Test
    void compareTo_lexicographic() {
        assertTrue(Ltree.fromSql("a").compareTo(Ltree.fromSql("b")) < 0);
        assertTrue(Ltree.fromSql("b").compareTo(Ltree.fromSql("a")) > 0);
        assertEquals(0, Ltree.fromSql("a").compareTo(Ltree.fromSql("a")));
    }

    @Test
    void isAncestorOf_trueWhenStrictPrefix() {
        Ltree parent = Ltree.fromSql("a");
        Ltree child = Ltree.fromSql("a.b");
        Ltree grand = Ltree.fromSql("a.b.c");
        assertTrue(parent.isAncestorOf(child));
        assertTrue(parent.isAncestorOf(grand));
        assertFalse(child.isAncestorOf(parent));
        assertFalse(parent.isAncestorOf(parent));
    }

    @Test
    void isAncestorOfAny_anyMatchSemantics() {
        Ltree root = Ltree.fromSql("root");
        List<Ltree> kids = List.of(Ltree.fromSql("other.x"), Ltree.fromSql("root.x"));
        assertTrue(root.isAncestorOfAny(kids));
        assertFalse(root.isAncestorOfAny(List.of(Ltree.fromSql("other.x"))));
    }

    @Test
    void escapeToLabel_overload_withKnownSpecialChars_keepsPreEncoded() {
        Set<String> known = Set.of("PERCENTSIGN");
        // déjà valide + déjà encodé : retourne tel quel
        assertEquals("a_PERCENTSIGN_b", Ltree.escapeToLabel("a_PERCENTSIGN_b", known));
    }

    @Test
    void escapeToLabel_overload_emptyKnownSet_fallsBackToDefault() {
        // chaîne pure lowercase _ => isEncodedString false, retourne tel quel après check syntaxe
        assertEquals("abc_def", Ltree.escapeToLabel("abc_def", Set.of()));
    }

    @Test
    void escapeToLabel_overload_nullKnownSet_usesDefault() {
        assertEquals("totoa", Ltree.escapeToLabel("totoa", null));
    }

    @Test
    void isEncodedString_overload_emptyKnownReturnsFalse() {
        assertFalse(Ltree.isEncodedString("anything", Set.of()));
    }

    @Test
    void isEncodedString_overload_lowercaseAlnum_returnsFalse() {
        assertFalse(Ltree.isEncodedString("abc_123", Set.of("PERCENTSIGN")));
    }

    @Test
    void isEncodedString_overload_matchesKnown() {
        assertTrue(Ltree.isEncodedString("foo_PERCENTSIGN_bar", Set.of("PERCENTSIGN")));
    }

    @Test
    void fromUnescapedString_nullKey_isEscapedSafely() {
        Ltree l = Ltree.fromUnescapedString(Ltree.NULL_KEY);
        // après transformation, ne doit pas être vide et doit être un label valide
        assertNotNull(l.getSql());
        assertFalse(l.getSql().isEmpty());
    }
}