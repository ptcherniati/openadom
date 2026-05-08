package fr.inra.oresing.persistence;

import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;

public class LtreeAdditionalTest {

    @Test
    public void testFromUnescapedString() {
        Ltree ltree = Ltree.fromUnescapedString("composition <5%/µg");
        Assert.assertEquals("composition_LESSTHANSIGN5PERCENTSIGNSOLIDUSMICROSIGNg", ltree.getSql());
    }

    @Test
    public void testFromUuid() {
        UUID uuid = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        Ltree ltree = Ltree.fromUuid(uuid);
        Assert.assertNotNull(ltree.getSql());
        Assert.assertFalse(ltree.getSql().contains("-"));
    }

    @Test
    public void testJoin() {
        Ltree prefix = Ltree.fromUnescapedString("root");
        Ltree suffix = Ltree.fromUnescapedString("child");
        Ltree joined = Ltree.join(prefix, suffix);
        Assert.assertEquals("root.child", joined.getSql());
    }

    @Test
    public void testFromSql() {
        Ltree ltree = Ltree.fromSql("valid_label");
        Assert.assertEquals("valid_label", ltree.getSql());
    }

    @Test
    public void testFromSqlMultiLevel() {
        Ltree ltree = Ltree.fromSql("level1.level2.level3");
        Assert.assertEquals("level1.level2.level3", ltree.getSql());
    }

    @Test
    public void testToString() {
        Ltree ltree = Ltree.fromSql("abc");
        Assert.assertEquals("abc", ltree.toString());
    }

    @Test
    public void testEquality() {
        Ltree a = Ltree.fromSql("abc");
        Ltree b = Ltree.fromSql("abc");
        Ltree c = Ltree.fromSql("xyz");
        Assert.assertEquals(a, b);
        Assert.assertNotEquals(a, c);
        Assert.assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testFromSqlWithInvalidLabelThrows() {
        try {
            Ltree.fromSql("invalid label");
            Assert.fail("une exception aurait dû être levée");
        } catch (IllegalStateException e) {
            // attendu
        }
    }

    @Test
    public void testEscapeToLabelStripsAccents() {
        String escaped = Ltree.escapeToLabel("température");
        Assert.assertFalse(escaped.contains("é"));
        Assert.assertTrue(escaped.matches("[a-zA-Z0-9_]+"));
    }

    @Test
    public void testEscapeToLabelReplacesSpaces() {
        String escaped = Ltree.escapeToLabel("hello world");
        Assert.assertEquals("hello_world", escaped);
    }
}
