package fr.inra.oresing.persistence;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class LtreeTest {

    @Test
    public void parseLabel() {
        String sql = Ltree.parseLabel("composition <5%/µg").getSql();
        Assert.assertEquals("composition_LESSTHANSIGN5PERCENTSIGNSOLIDUSMICROSIGNg", sql);
    }
}