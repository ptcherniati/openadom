/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.inra.oresing.checker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 *
 * @author ptcherniati
 */
public class FloatCheckerTest {

    /**
     * Test of check method, of class FloatChecker.
     */
    @Test
    public void testCheckWithFloatValue() throws Exception {
        System.out.println("checkWithFloatValue");
        String value = "45.231";
        FloatChecker instance = new FloatChecker();
        Float expResult = 45.231F;
        Float result = instance.check(value);
        assertEquals(expResult, result);
    }
    
    /**
     * Test of check method, of class FloatChecker.
     */
    @Test(expected = CheckerException.class)
    public void testCheckWithNotFloatValue() throws Exception {
        System.out.println("checkWithNotFloatValue");
        String value = "quarante-cinq";
        FloatChecker instance = new FloatChecker();
        Float result = instance.check(value);
        assertNull(result);
    }
}
