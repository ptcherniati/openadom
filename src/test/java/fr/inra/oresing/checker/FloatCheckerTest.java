/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fr.inra.oresing.checker;

import java.util.Map;
import java.util.TreeMap;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author ptcherniati
 */
public class FloatCheckerTest {
    
    public FloatCheckerTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() {
    }
    
    @After
    public void tearDown() {
    }
    /**
     * Test of check method, of class FloatChecker.
     */
    @Test(expected = CheckerException.class)
    public void testCheckWithNullValue() throws Exception {
        System.out.println("checkWithNullValue");
        String value = null;
        FloatChecker instance = new FloatChecker();
        Float expResult = null;
        Float result = instance.check(value);
        assertNull(result);
    }
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
    /**
     * Test of check method, of class FloatChecker.
     */
    @Test
    public void testCheckWithDefaultValue() throws Exception {
        System.out.println("checkWithDefaultValue");
        String value = null;
        FloatChecker instance = new FloatChecker();
        TreeMap<String, String> params = new TreeMap<String, String>();
        params.put(FloatChecker.PARAM_DEFAULT, "45.236");
        instance.setParam(params);
        Float expResult = 45.236F;
        Float result = instance.check(value);
        assertEquals(expResult, result);
    }
}
