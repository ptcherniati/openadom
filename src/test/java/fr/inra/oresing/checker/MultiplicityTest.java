package fr.inra.oresing.checker;

import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import org.junit.Assert;
import org.junit.Test;

public class MultiplicityTest {

    @Test
    public void testGetErrorForOne() {
        SiOreIllegalArgumentException error = Multiplicity.getError(Multiplicity.ONE);
        Assert.assertEquals("badMultiplicity", error.getMessage());
        Assert.assertEquals(Multiplicity.ONE, error.getParams().get("multiplicity"));
    }

    @Test
    public void testGetErrorForMany() {
        SiOreIllegalArgumentException error = Multiplicity.getError(Multiplicity.MANY);
        Assert.assertEquals("badMultiplicity", error.getMessage());
        Assert.assertEquals(Multiplicity.MANY, error.getParams().get("multiplicity"));
    }

    @Test
    public void testKnownMultiplicityContainsAllValues() {
        SiOreIllegalArgumentException error = Multiplicity.getError(Multiplicity.ONE);
        @SuppressWarnings("unchecked")
        java.util.Set<String> known = (java.util.Set<String>) error.getParams().get("knownMultiplicity");
        Assert.assertTrue(known.contains("ONE"));
        Assert.assertTrue(known.contains("MANY"));
    }

    @Test
    public void testValues() {
        Assert.assertEquals(2, Multiplicity.values().length);
        Assert.assertNotNull(Multiplicity.valueOf("ONE"));
        Assert.assertNotNull(Multiplicity.valueOf("MANY"));
    }
}
