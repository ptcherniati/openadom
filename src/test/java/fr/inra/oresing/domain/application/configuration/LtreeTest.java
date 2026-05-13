package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core.config")
@Tag("domain.model")
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

    /*@Test
    void assertThatAStringWithCompositeLTreeCanBeEncodedTwice(){
        String label = "toto.titi.tutu";
        Ltree nk = Ltree.fromUnescapedString(label);
        Assert.assertEquals(label, nk.getSql());
    }*/

    @ParameterizedTest(name = "{0} match an encodingString")
    @ValueSource(strings = {"%", ">", "$", "?", "&", "@", "°", "µ"})
    void testIsEcodedString(String aSign) {
        String aChar = Ltree.fromUnescapedString(aSign).getSql();
        assertTrue(Ltree.isEncodedString(aChar));
    }

    @ParameterizedTest(name = "{0} doesn't match an encodingString")
    @ValueSource(strings = {"_", "A", "2", "a", "²"})
        // Les caractères ici ne doivent pas être considérés comme encodés.
        // Exemple : '²' (SUPERSCRIPT TWO) est traité comme un caractère normal ici.
    void testIsNotEncodedString(String aSign) {
        String aChar = Ltree.fromUnescapedString(aSign).getSql();
        assertFalse(Ltree.isEncodedString(aChar));
    }
}