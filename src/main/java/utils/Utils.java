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
package utils;

import static boa.gui.GUI.logger;
import boa.gui.imageInteraction.ImageObjectInterface;
import configuration.parameters.FileChooser;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.Morphium;
import ij.gui.Plot;
import image.Image;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.swing.DefaultListModel;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JList;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.ListModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import measurement.extraction.DataExtractor;

/**
 *
 * @author jollion
 */
public class Utils {
    
    private final static Pattern p = Pattern.compile("[^a-z0-9_-]", Pattern.CASE_INSENSITIVE);
    public static String getStringArrayAsString(String... stringArray) {
        if (stringArray==null) return "[]";
        String res="[";
        for (int i = 0; i<stringArray.length; ++i) {
            if (i!=0) res+="; ";
            res+=stringArray[i];
        }
        res+="]";
        return res;
    }
    
    public static String getStringArrayAsStringTrim(int maxSize, String... stringArray) {
        String array = getStringArrayAsString(stringArray);
        if (maxSize<4) maxSize=5;
        if (array.length()>=maxSize) {
            return array.substring(0, maxSize-4)+"...]";
        } else return array;
    }
    
    public static int getIndex(String[] array, String key) {
        if (key==null) return -1;
        for (int i = 0; i<array.length; i++) if (key.equals(array[i])) return i;
        return -1;
    }
    
    public static boolean isValid(String s, boolean allowSpecialCharacters) {
        if (s==null || s.length()==0) return false;
        if (allowSpecialCharacters) return true;
        Matcher m = p.matcher(s);
        return !m.find();
    }
    public static String getSelectedString(JComboBox jcb) {
        return (jcb.getSelectedIndex()==-1)?null : (String)jcb.getSelectedItem();
    }
    
    public static String formatInteger(int paddingSize, int number) {
        return String.format(Locale.US, "%0" + paddingSize + "d", number);
    }
    
    public static String formatDoubleScientific(int significantDigits, double number) {
        String f = "0.";
        for (int i = 0; i<significantDigits; ++i) f+="#";
        f+="E0";
        NumberFormat formatter = new DecimalFormat(f);
        return formatter.format(number);
    }
    
    public static String formatDoubleScientific(double number) {
        NumberFormat formatter = new DecimalFormat("0.##E0");
        return formatter.format(number);
    }
    
    public static int[] toArray(List<Integer> arrayList, boolean reverseOrder) {
        int[] res=new int[arrayList.size()];
        if (reverseOrder) {
            int idx = res.length-1;
            for (int s : arrayList) res[idx--] = s;
        } else for (int i = 0; i<res.length; ++i) res[i] = arrayList.get(i);
        return res;
    }
    public static List<Integer> toList(int[] array) {
        if (array==null || array.length==0) return new ArrayList<Integer>();
        ArrayList<Integer> res = new ArrayList<>(array.length);
        for (int i : array) res.add(i);
        return res;
    }
    
    public static double[] toDoubleArray(List<? extends Number> arrayList, boolean reverseOrder) {
        double[] res=new double[arrayList.size()];
        if (reverseOrder) {
            int idx = res.length-1;
            for (Number s : arrayList) res[idx--] = s.doubleValue();
        } else for (int i = 0; i<res.length; ++i) res[i] = arrayList.get(i).doubleValue();
        return res;
    }
    public static float[] toFloatArray(List<? extends Number> arrayList, boolean reverseOrder) {
        float[] res=new float[arrayList.size()];
        if (reverseOrder) {
            int idx = res.length-1;
            for (Number s : arrayList) res[idx--] = s.floatValue();
        } else for (int i = 0; i<res.length; ++i) res[i] = arrayList.get(i).floatValue();
        return res;
    }
    
    public static String[] toStringArray(Enum[] array) {
        String[] res = new String[array.length];
        for (int i = 0;i<res.length;++i) res[i]=array[i].toString();
        return res;
    }
    
    public static <T> String toStringArray(T[] array) {
        if (array==null||array.length==0) return "[]";
        String res = "[";
        for (int i = 0; i<array.length-1; ++i) res+=array[i].toString()+"; ";
        res+=array[array.length-1]+"]";
        return res;
    }
    public static <T> String toStringList(Collection<T> array) {
        return toStringList(array, o->o.toString());
    }
    public static <T> String toStringArray(T[] array, Function<T, Object> toString) {
        if (array.length==0) return "[]";
        String res = "[";
        for (int i = 0; i<array.length; ++i) {
            String s=null;
            if (array[i]!=null) {
                Object o = toString.apply(array[i]);
                if (o!=null) s = o.toString();
            }
            res+=s+ (i<array.length-1 ? "; " : "]");
        }
        return res;
    }
    public static <T> String toStringList(Collection<T> array, Function<T, Object> toString) {
        if (array.isEmpty()) return "[]";
        String res = "[";
        Iterator<T> it = array.iterator();
        while(it.hasNext()) {
            T t = it.next();
            String s=null;
            if (t!=null) {
                Object o = toString.apply(t);
                if (o!=null) s = o.toString();
            }
            if (s==null) s = "NA";
            res+=s+(it.hasNext()?";":"]");
        }
        return res;
    }
    public static <K, V> String toStringMap(Map<K, V> map, Function<K, String> toStringKey, Function<V, String> toStringValue) {
        if (map.isEmpty()) return "[]";
        String res = "[";
        Iterator<Entry<K, V>> it = map.entrySet().iterator();
        while(it.hasNext()) {
            Entry<K, V> e = it.next();
            res+=(e.getKey()==null?"NA" : toStringKey.apply(e.getKey()))+"->"+(e.getValue()==null?"NA":toStringValue.apply(e.getValue()))+(it.hasNext() ? ";":"]");
        }
        return res;
    }
    public static <T> String toStringArray(int[] array) {
        return toStringArray(array, "[", "]", "; ").toString();
    }
    
    public static <T> String toStringArray(double[] array) {
        return toStringArray(array, "[", "]", "; ", DataExtractor.numberFormater).toString();
    }

    public static <T> StringBuilder toStringArray(double[] array, String init, String end, String sep, Function<Number, String> numberFormatter) {
        StringBuilder sb = new StringBuilder(init);
        if (array.length==0) {
            sb.append(end);
            return sb;
        }
        for (int i = 0; i<array.length-1; ++i) {
            sb.append(numberFormatter.apply(array[i]));
            sb.append(sep);
        }
        sb.append(numberFormatter.apply(array[array.length-1]));
        sb.append(end);
        return sb;
    }
    public static <T> StringBuilder toStringArray(int[] array, String init, String end, String sep) {
        StringBuilder sb = new StringBuilder(init);
        if (array.length==0) {
            sb.append(end);
            return sb;
        }
        for (int i = 0; i<array.length-1; ++i) {
            sb.append(array[i]);
            sb.append(sep);
        }
        sb.append(array[array.length-1]);
        sb.append(end);
        return sb;
    }
    
    public static<T> ArrayList<T> reverseOrder(ArrayList<T> arrayList) {
        ArrayList<T> res = new ArrayList<T>(arrayList.size());
        for (int i = arrayList.size()-1; i>=0; --i) res.add(arrayList.get(i));
        return res;
    }
    
    public static <K, V> ArrayList<K> getKeys(Map<K, V> map, V value) {
        ArrayList<K> res = new ArrayList<K>();
        for (Entry<K, V> e : map.entrySet()) if (e.getValue().equals(value)) res.add(e.getKey());
        return res;
    }
    
    public static <K, V> ArrayList<K> getKeys(Map<K, V> map, Collection<V> values) {
        ArrayList<K> res = new ArrayList<K>();
        for (Entry<K, V> e : map.entrySet()) if (values.contains(e.getValue())) res.add(e.getKey());
        return res;
    }
    
    public static void addHorizontalScrollBar(JComboBox box) {
        Object comp = box.getUI().getAccessibleChild(box, 0);
        if (!(comp instanceof JPopupMenu)) return;
        JPopupMenu popup = (JPopupMenu) comp;
        int n = popup.getComponentCount();
        int i = 0;
        while (i<n) {
            if (popup.getComponent(i) instanceof JScrollPane) {
                JScrollPane scrollPane = (JScrollPane) popup.getComponent(i);
                scrollPane.setHorizontalScrollBar(new JScrollBar(JScrollBar.HORIZONTAL));
                scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            }
            i++;
        }
    }
    
    public static Color[] generatePalette(int colorNb, boolean excludeReds) {
        Color[] res = new Color[colorNb];
        double goldenRatioConjugate = 0.618033988749895;
        double h = 0.33;
        for(int i = 0; i <colorNb; ++i) {
            res[i] = Color.getHSBColor((float)h, 0.99f, 0.99f);
            if (excludeReds) {  // pure red = 0;
                h=incrementColor(h, goldenRatioConjugate);
                while(h<0.05) h=incrementColor(h, goldenRatioConjugate);
            } else h=incrementColor(h, goldenRatioConjugate);
            
        }
        return res;
    }
    
    public static boolean isCtrlOrShiftDown(MouseEvent e) {
        return (e.getModifiers()&InputEvent.CTRL_DOWN_MASK)!=0 || (e.getModifiers()&InputEvent.ALT_DOWN_MASK)!=0 ;
    }
    
    public static void addToSelectionPaths(JTree tree, TreePath... pathToSelect) {
        addToSelectionPaths(tree, Arrays.asList(pathToSelect));
    }
    public static void addToSelectionPaths(JTree tree, List<TreePath> pathToSelect) {
        TreePath[] sel = tree.getSelectionPaths();
        if (sel==null) tree.setSelectionPaths(pathToSelect.toArray(new TreePath[pathToSelect.size()]));
        else {
            ArrayList<TreePath> list = new ArrayList<TreePath>(pathToSelect.size()+sel.length);
            list.addAll(Arrays.asList(sel));
            list.addAll(pathToSelect);
            tree.setSelectionPaths(list.toArray(new TreePath[list.size()]));
        }
    }
    
    public static void removeFromSelectionPaths(JTree tree, TreePath... pathToDeSelect) {
        removeFromSelectionPaths(tree, Arrays.asList(pathToDeSelect));
    }
    public static void removeFromSelectionPaths(JTree tree, List<TreePath> pathToDeSelect) {
        TreePath[] sel = tree.getSelectionPaths();
        if (sel==null) return;
        else {
            ArrayList<TreePath> list = new ArrayList<TreePath>(sel.length);
            for (TreePath p : sel) if (!pathToDeSelect.contains(p)) list.add(p);
            tree.setSelectionPaths(list.toArray(new TreePath[list.size()]));
        }
    }
    
    public static boolean isSelected(JTree tree, TreePath path) {
        if (tree.getSelectionCount()!=0) return Arrays.asList(tree.getSelectionPaths()).contains(path);
        else return false;
    }
    
    
    
    public static <T> void setSelectedValues(Collection<T> selection, JList list, DefaultListModel<T> model) {
        List<Integer> selectedIndicies = new ArrayList<Integer>();
        for (T s : selection) {
            int i = model.indexOf(s);
            if (i!=-1) selectedIndicies.add(i);
        }
        //if (!selectedIndicies.isEmpty()) {
            int[] res = Utils.toArray(selectedIndicies, false);
            list.setSelectedIndices(res);
            //logger.debug("set selected indices on list: {}", res);
        //}
    }
    
    public static <T> List<T> asList(ListModel<T> model) {
        int s = model.getSize();
        List<T> res = new ArrayList<>(s);
        for (int i = 0; i<s; ++i) res.add(model.getElementAt(i));
        return res;
    }
    
    private static double incrementColor(double h, double goldenRatioConjugate) {return (h+goldenRatioConjugate)%1;}
    
    public static void plotProfile(Image image, int z, int coord, boolean alongX) {
        double[] x;
        double[] y;
        if (alongX) {
            x=new double[image.getSizeX()];
            y=new double[image.getSizeX()];
            for (int i = 0; i<x.length; ++i) {
                x[i]=i;
                y[i]=image.getPixel(i, coord, z);
            }
        } else {
            x=new double[image.getSizeY()];
            y=new double[image.getSizeY()];
            for (int i = 0; i<x.length; ++i) {
                x[i]=i;
                y[i]=image.getPixel(coord, i, z);
            }
        }
        new Plot(image.getName(), "coord", "value", x, y).show();
    }
    
    public static void plotProfile(String title, int[] values) {
        if (values.length<=1) return;
        double[] doubleValues = ArrayUtil.toDouble(values);
        double v = doubleValues[0];
        int idx = 0; 
        while (idx<doubleValues.length && doubleValues[idx]==v) ++idx;
        if (idx==doubleValues.length) return;
        double[] x=new double[doubleValues.length];
        for (int i = 0; i<x.length; ++i) x[i]=i;
        new Plot(title, "coord", "value", x, doubleValues).show();
    }
    
    public static void plotProfile(String title, float[] values) {
        if (values.length<=1) return;
        float v = values[0];
        int idx = 0; 
        while (idx<values.length && values[idx]==v) ++idx;
        if (idx==values.length) return;
        float[] x=new float[values.length];
        for (int i = 0; i<x.length; ++i) x[i]=i;
        new Plot(title, "coord", "value", x, values).show();
    }
    
    public static void plotProfile(String title, double[] values) {
        if (values.length<=1) return;
        double v = values[0];
        int idx = 0; 
        while (idx<values.length && values[idx]==v) ++idx;
        if (idx==values.length) return; // cannot be ploted if one single value
        double[] x=new double[values.length];
        for (int i = 0; i<x.length; ++i) x[i]=i;
        new Plot(title, "coord", "value", x, values).show();
    }
    
    public static void deleteDirectory(String dir) {
        if (dir!=null) deleteDirectory(new File(dir));
    }
    public static void deleteDirectory(File dir) { //recursive delete, because java's native function wants the dir to be empty to delete it
        if (dir==null || !dir.exists()) return;
        if (dir.isFile()) dir.delete();
        else {
            for (File f : dir.listFiles()) deleteDirectory(f);
            dir.delete();
        }
    }
    
    public static File seach(String path, String fileName, int recLevels) {
        File f= new File(path);
        if (!f.exists()) return null;
        if (f.isDirectory()) return search(new ArrayList<File>(1){{add(f);}}, fileName, recLevels, 0);
        else if (f.getName().equals(fileName)) return f;
        else return null;
    }
    private static File search(List<File> files, String fileName, int recLevels, int currentLevel) {
        for (File f : files) {
            File[] ff = f.listFiles((dir, name) -> fileName.equals(name));
            if (ff.length>0) return ff[0];
        }
        if (currentLevel==recLevels) return null;
        List<File> nextFiles = new ArrayList<>();
        for (File f : files) {
            File[] ff = f.listFiles(file -> file.isDirectory());
            if (ff.length>0) nextFiles.addAll(Arrays.asList(ff));
        }
        return search(nextFiles, fileName, recLevels, currentLevel+1);
    }
    
    public static File[] chooseFiles(String dialogTitle, String directory, FileChooser.FileChooserOption option, Component parent) {
        final JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(option.getOption());
        //fc.setFileHidingEnabled(false);
        fc.setMultiSelectionEnabled(option.getMultipleSelectionEnabled());
        if (directory != null) fc.setCurrentDirectory(new File(directory));
        if (dialogTitle!=null) fc.setDialogTitle(dialogTitle);
        else fc.setDialogTitle("Choose Files");
        int returnval = fc.showOpenDialog(parent);
        if (returnval == JFileChooser.APPROVE_OPTION) {
            File[] res;
            if (option.getMultipleSelectionEnabled()) res = fc.getSelectedFiles();
            else res = new File[]{fc.getSelectedFile()};
            return res;
        } else return null;
    }
    
    public static File chooseFile(String dialogTitle, String directory, FileChooser.FileChooserOption option, Component parent) {
        File[] res = chooseFiles(dialogTitle,directory, option,  parent);
        if (res!=null) return res[0];
        else return null;
    }
    
    public static File getOneDir(File[] files) {
        for (File f : files) {
            if (f.isDirectory()) return f;
        }
        if (files.length>0) return files[0].getParentFile();
        return null;
    }
    
    public static String[] convertFilesToString(File[] files) {
        if (files ==null) return null;
        String[] res = new String[files.length];
        for (int i = 0; i<files.length; ++i) res[i] = files[i].getAbsolutePath();
        return res;
    }
    
    public static int[] copyArray(int[] source) {
        int[] res = new int[source.length];
        System.arraycopy(source, 0, res, 0, source.length);
        return res;
    }
    
    public static void expandTree(JTree tree) {
        for (int row = 0; row < tree.getRowCount(); row++) tree.expandRow(row);
    }
    
    public static void expandAll(JTree tree) {
        TreeNode root = (TreeNode) tree.getModel().getRoot();
        expandAll(tree, new TreePath(root), null);
      }
    
    public static void expandAll(JTree tree, TreePath parent, ArrayList<TreePath> expandedPath) {
        TreeNode node = (TreeNode) parent.getLastPathComponent();
        if (expandedPath!=null) expandedPath.add(parent);
        
        if (node.getChildCount() >= 0) {
          for (Enumeration e = node.children(); e.hasMoreElements();) {
            TreeNode n = (TreeNode) e.nextElement();
            TreePath path = parent.pathByAddingChild(n);
            expandAll(tree, path, expandedPath);
          }
        }
        tree.expandPath(parent);
        // tree.collapsePath(parent);
    }
    
    public static TreePath getTreePath(TreeNode node) {
        ArrayList<TreeNode> path = new ArrayList<TreeNode>();
        while(node!=null) {
            path.add(node);
            node = node.getParent();
        }
        path = Utils.reverseOrder(path);
        return new TreePath(path.toArray(new TreeNode[path.size()]));
    }
    
    public static <T> void removeDuplicates(Collection<T> list, boolean keepOrder) {
        Collection<T> set = keepOrder? new LinkedHashSet<T>(list) : new HashSet<T>(list);
        list.clear();
        list.addAll(set);
    }
    
    public static <K, V> Entry<K, V> removeFromMap(Map<K, V> map, K key) {
        Iterator<Entry<K, V>> it = map.entrySet().iterator();
        while(it.hasNext()) {
            Entry<K, V> e = it.next();
            if (e.getKey().equals(key)) {
                it.remove();
                return e;
            }
        }
        return null;
    }
    public static <K, V> Entry<K, V> getFromMap(Map<K, V> map, K key) {
        Iterator<Entry<K, V>> it = map.entrySet().iterator();
        while(it.hasNext()) {
            Entry<K, V> e = it.next();
            if (e.getKey().equals(key)) return e;
        }
        return null;
    }
    public static <K, V> boolean mapContains(Map<K, V> map, K key) {
        Iterator<Entry<K, V>> it = map.entrySet().iterator();
        while(it.hasNext()) {
            Entry<K, V> e = it.next();
            if (e.getKey().equals(key)) return true;
        }
        return false;
    }

    public static String removeExtension(String s) {
        int idx = s.indexOf(".");
        if (idx > 0) {
            return s.substring(0, idx);
        }
        return s;
    }
    public static String getExtension(String s) {
        int idx = s.indexOf(".");
        if (idx > 0) return s.substring(idx+1, s.length());
        return "";
    }
    
    public static String getVersion(Object o) {
        String version = null;

        // try to load from maven properties first
        try {
            Properties p = new Properties();
            InputStream is = o.getClass().getResourceAsStream("/META-INF/maven/ljp/functional-bioimage-core/pom.properties");
            if (is != null) {
                p.load(is);
                version = p.getProperty("version", "");
            }
        } catch (Exception e) {
            // ignore
        }

        // fallback to using Java API
        if (version == null) {
            Package aPackage = o.getClass().getPackage();
            if (aPackage != null) {
                version = aPackage.getImplementationVersion();
                if (version == null) {
                    version = aPackage.getSpecificationVersion();
                }
            }
        }

        if (version == null) {
            // we could not compute the version so use a blank
            version = "";
        }

        return version;
    } 

    public static String format(Number n, int digits) {
        if (n instanceof Integer) {
            return n.toString();
        } else {
            double abs = Math.abs(n.doubleValue());
            if (Double.isInfinite(abs) || Double.isNaN(abs)) return DataExtractor.NaN;
            double pow = Math.pow(10, digits);
            if (abs > 1000 || (abs<0.1 && ((int)(abs*pow))/pow!=abs)) {
                return String.format(java.util.Locale.US, "%."+ digits+ "E", n);
            } else {
                return String.format(java.util.Locale.US, "%."+ digits+"f", n);
            }
        }
    }
    public static String format4(Number n) {
        if (n instanceof Integer) {
            return n.toString();
        } else {
            double abs = Math.abs(n.doubleValue());
            if (Double.isInfinite(abs) || Double.isNaN(abs)) return DataExtractor.NaN;
            if (abs > 1000 || (abs<0.1 && ((int)(abs*10000))/10000!=abs)) {
                return String.format(java.util.Locale.US, "%.4E", n);
            } else {
                return String.format(java.util.Locale.US, "%.4f", n);
            }
        }
    }
    
    public static <T> T getFirst(Collection<T> coll, Function<T, Boolean> fun) {
        for (T t : coll) if (fun.apply(t)) return t;
        return null;
    }
    public static <K, V> V getFirst(Map<K, V> map, Function<K, Boolean> fun) {
        for (K t : map.keySet()) if (fun.apply(t)) return map.get(t);
        return null;
    }
    public static <K, V> void removeIf(Map<K, V> map, BiFunction<K, V, Boolean> fun) {
        Iterator<Entry<K, V>> it = map.entrySet().iterator();
        while(it.hasNext()) {
            Entry<K, V> e = it.next();
            if (fun.apply(e.getKey(), e.getValue())) it.remove();
        }
    }
    public static <V> List<V> flattenMap(Map<?, ? extends Collection<V>> map) {
        List<V> l = new ArrayList<>();
        for (Collection<V> c : map.values()) l.addAll(c);
        return l;
    }
    public static <V> Set<V> flattenMapSet(Map<?, ? extends Collection<V>> map) {
        Set<V> l = new HashSet<>();
        for (Collection<V> c : map.values()) l.addAll(c);
        return l;
    }
    
    public static <T, K> List<K> apply(Collection<T> list, Function<T, K> func) {
        if (list==null) return null;
        if (list.isEmpty()) return Collections.EMPTY_LIST;
        return list.stream().map(func).collect(Collectors.toList());
        /*List<K> res = new ArrayList<>(list.size());
        for (T t : list)  res.add(func.apply(t));
        return res;*/
    }
    public static <T, K> List<K> applyWithNullCheck(Collection<T> list, Function<T, K> func) {
        if (list==null) return null;
        if (list.isEmpty()) return Collections.EMPTY_LIST;
        return list.stream().map(func).filter(e -> e!=null).collect(Collectors.toList());
        /*List<K> res = new ArrayList<>(list.size());
        for (T t : list)  {
            K k = func.apply(t);
            if (k!=null) res.add(k);
        }
        return res;*/
    }
    public static <T> T[] apply(T[] array, T[] outputArray, Function<T, T> func) {
        if (array==null) return null;
        for (int i = 0; i<array.length; ++i) outputArray[i] = func.apply(array[i]);
        return outputArray;
    }
    
}
