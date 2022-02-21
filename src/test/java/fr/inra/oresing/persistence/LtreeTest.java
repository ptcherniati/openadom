package fr.inra.oresing.persistence;

import org.junit.Assert;
import org.junit.Test;

public class LtreeTest {

    @Test
    public void parseLabel() {
        String sql = Ltree.fromUnescapedString("composition <5%/µg").getSql();
        Assert.assertEquals("composition_LESSTHANSIGN5PERCENTSIGNSOLIDUSMICROSIGNg", sql);
    }
}