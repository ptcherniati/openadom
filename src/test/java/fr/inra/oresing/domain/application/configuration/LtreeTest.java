package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@Tag("core.config")
class LtreeTest {
    @Test
    void assertThatAStringWithInvalidCharactersCanBeEncodedTwice() {
        String label = "TOTOé_°_%_>_²_؇_?";
        Ltree nk = Ltree.fromUnescapedString(label);
        String encodedString = "totoe_DEGREESIGN_PERCENTSIGN_GREATERTHANSIGN_SUPERSCRIPTTWO_ARABICINDICFOURTHROOT_QUESTIONMARK";
        Ltree secondEncoding = Ltree.fromUnescapedString(encodedString);
        assertEquals(encodedString, nk.getSql());
        assertEquals(encodedString, secondEncoding.getSql());
    }

    @Test
    public void parseLabel() {
        final String sql = Ltree.fromUnescapedString("composition <5%/µg").getSql();
        assertEquals("composition_LESSTHANSIGN5PERCENTSIGNSOLIDUSMICROSIGNg", sql);
    }

    /*@Test
    void assertThatAStringWithCompositeLTreeCanBeEncodedTwice(){
        String label = "toto.titi.tutu";
        Ltree nk = Ltree.fromUnescapedString(label);
        Assert.assertEquals(label, nk.getSql());
    }*/
    @ParameterizedTest(name = "{0} match an encodingString")
    @ValueSource(strings = {"°", "%", ">", "²", "$", "?", "&", "@", "°", "µ"})
    void testIsEcodedString(String aSign) {
        String aChar = Ltree.fromUnescapedString(aSign).getSql();
        assertTrue(Ltree.isEncodedString(aChar));
    }

    @ParameterizedTest(name = "{0} doesn't match an encodingString")
    @ValueSource(strings = {"_", "A", "2", "a"})
    void testIsNotEncodedString(String aSign) {
        String aChar = Ltree.fromUnescapedString(aSign).getSql();
        assertFalse(Ltree.isEncodedString(aChar));
    }
}