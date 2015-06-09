/*
 * Copyright (C) 2015 ImageJ
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package JUnittest;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author jollion
 */
public class CalculatorImplTest {
    
    public CalculatorImplTest() {
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
     * Test of multiply method, of class CalulatorImp.
     */
    @org.junit.Test
    public void testMultiply() {
        System.out.println("multiply");
        int a = 2;
        int b = 3;
        CalculatorImpl instance = new CalculatorImpl();
        int expResult = 6;
        int result = instance.multiply(a, b);
        assertEquals(expResult, result);

    }

    /**
     * Test of divide method, of class CalulatorImp.
     */
    @org.junit.Test
    public void testDivide() {
        System.out.println("divide");
        int a = 0;
        int b = 0;
        CalculatorImpl instance = new CalculatorImpl();
        int expResult = 0;
        int result = instance.divide(a, b);
        assertEquals(expResult, result);
    }
    
    @Test (expected = ArithmeticException.class)
    public final void testDivideByZero() {
        Calculator calc = new CalculatorImpl();
        int a, b, res;
        a = 5;
        b = 0;
        res = 0;
        assertEquals("b nul", calc.divide(a, b), res);
        a = 0;
        b = 0;
        res = 0;
        assertEquals(" a et b nul", calc.divide(a, b), res);
    }

    /**
     * Test of add method, of class CalulatorImp.
     */
    @org.junit.Test
    public void testAdd() {
        System.out.println("add");
        int a = 0;
        int b = 0;
        CalculatorImpl instance = new CalculatorImpl();
        int expResult = 0;
        int result = instance.add(a, b);
        assertEquals(expResult, result);
    }

    /**
     * Test of substract method, of class CalulatorImp.
     */
    @org.junit.Test
    public void testSubstract() {
        System.out.println("substract");
        int a = 0;
        int b = 0;
        CalculatorImpl instance = new CalculatorImpl();
        int expResult = 0;
        int result = instance.substract(a, b);
        assertEquals(expResult, result);
    }
    
}
