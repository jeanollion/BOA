/*
 * Copyright (C) 2015 jollion
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
package TestUtils;

import static TestUtils.Utils.logger;
import image.ImageReader;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import testPlugins.dummyPlugins.DummySegmenter;
import utils.Pair;
import static utils.Utils.removeFromMap;

/**
 *
 * @author jollion
 */
public class TestClass {
    @Test
    public void testDIv() {
        double a = 100.12;
        int digits = 3;
        logger.debug("a: {}, formatted: {}", a, String.format("%."+digits+"f", a));
    }
    //@Test
    public void testNaN() {
        double a = Double.NaN;
        double b = 1;
        logger.debug("a<b: {}, a>b: {}", a<b, a>b);
    }
    //@Test
    public void testReadND2() {
        String inputDir = "/media/jollion/4336E5641DA22135/LJP/phase/phase140115/6300_mutH_LB-LR62rep-15012014_nd2/mg6300mutH_LB_lr62rep_oil37.nd2";
        File f = new File(inputDir);
        logger.debug("file: {}, exists? {}", f.getAbsolutePath(), f.exists());
        ImageReader r = new ImageReader(inputDir);
        logger.debug("features: {}", (Object)r.getSTCXYZNumbers());
    }
    
    //@Test
    public void testRegex() {
        String[] tests = new String[]{"xy1", "lalaxy1", "xy23lala", "lalaxy1lala", "xyz1n", "xy"};
        Pattern p = Pattern.compile(".*xy(\\d+).*");
        for (String test : tests) {
            Matcher m = p.matcher(test);
            boolean b = m.find();
            logger.debug("String: {}, match: {}, res: {}", test, b, b? m.group(1) : null);
        }
        
        //String[] array = {"name1","name2","name3","name4", "name5", "name2"};
        //Map<String, List<String>> l = Arrays.stream(array).collect(Collectors.groupingBy(s -> s)); //.forEach((k, v) -> System.out.println(k+" "+v.size()));

    }
    //@Test
    public void testPair() {
        List<Pair<Integer, Double>> l = new ArrayList<Pair<Integer, Double>>();
        Pair<Integer, Double> p1 = new Pair(1, 1.0);
        Pair<Integer, Double> p2 = new Pair(2, 3.0);
        l.add(p1);
        l.add(p2);
        Pair k = new Pair(1, null);
        Pair v = new Pair(null, 3.0);
        Map<Pair<Integer, Double>, Integer> map = new HashMap<Pair<Integer, Double>, Integer>();
        map.put(p1, 5);
        map.put(p2, 6);
        assertTrue("pair with key only", k.equals(p1));
        assertTrue("pair with key only 2", !p2.equals(k));
        assertTrue("pair with value only", l.get(1).equals(v));
        assertTrue("pair with value only 2", !p1.equals(v));
        assertEquals("get within list with key", 0, l.indexOf(k)); 
        assertEquals("get within list with value", 1, l.indexOf(v));
        assertTrue("get within map p1", map.containsKey(p1));
        Integer d = (Integer)utils.Utils.removeFromMap(map, k).getValue();
        assertTrue("get within map using iterator", 5==d);
        assertTrue("removed from map", map.size()==1);
        
    }
    //@Test 
    public void testMath() {
        int aa = 5;
        int bb = 2;
        logger.info("div 5/2= {}" , aa / bb);
        logger.info("floor (1.0066889632106963) = {} ", Math.ceil(1.0066889632106963));
    }
    //@Test
    public void test() {
        Number n = -1d;
        logger.info("bytevalue of -1: {} " , n.byteValue()& 0xff);
        logger.info("shortvalue of -1: {} " , n.shortValue()& 0xffff);
        logger.info("intvalue of -1: {} " , n.intValue());
        
        
        logger.info("(byte)255: {}" , ((byte)255)& 0xff);
        logger.info("(byte)0: {}" , ((byte)0)& 0xff);
        logger.info("7+8/2: {}" , (7+8)/2);
    }
    //@Test
    public void testInstance() {
        double[][] matrix = new double[2][2];
        logger.info("[] instanceof []: {}", (Object)matrix[0] instanceof double[]);
        logger.info("[][] instanceof [][]: {}", (Object)matrix instanceof double[][]);
        logger.info("[][] instanceof []: {}", (Object)matrix instanceof double[]);
        logger.info("[] is array: {}", matrix[0].getClass().isArray());
        
        logger.info("[][] is array: {}", matrix.getClass().isArray());
        
        Double d = 3d;
        logger.info("Double instanceof Number: {}", d instanceof Number);
    }
    
    
    
    //@Test
    public void testFor() {
        for (int i : getValues()) logger.debug("value: {}", i);
        int[] array = getValues();
        for (int i = 0; i<getSize(array); ++i) logger.debug("value: {}", array[i]);
    }
    private static int[] getValues() {
        logger.debug("getValues!!");
        return new int[]{1, 2, 3};
    }
    private static int getSize(int[] array) {
        logger.debug("getSize!!");
        return array.length;
    }
}
