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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    
    public static String formatDouble(int paddingSize, double number) {
        int dec = (int)Math.pow(10, paddingSize);
        return String.valueOf((double)((int)number*dec)/(double)dec);
        // TODO utiliser les fonction format de java..
    }
    
    public static int[] toArray(List<Integer> arrayList, boolean reverseOrder) {
        int[] res=new int[arrayList.size()];
        if (reverseOrder) {
            int idx = res.length-1;
            for (int s : arrayList) res[idx--] = s;
        } else for (int i = 0; i<res.length; ++i) res[i] = arrayList.get(i);
        return res;
    }
    
    public static String[] toStringArray(Enum[] array) {
        String[] res = new String[array.length];
        for (int i = 0;i<res.length;++i) res[i]=array[i].toString();
        return res;
    }
    
    public static <T> String toStringArray(T[] array) {
        String res = "[";
        for (int i = 0; i<array.length-1; ++i) res+=array[i].toString()+"; ";
        res+=array[array.length-1]+"]";
        return res;
    }
    
    public static <T> String toStringArray(int[] array) {
        return toStringArray(array, "[", "]", "; ");
    }
    
    public static <T> String toStringArray(int[] array, String init, String end, String sep) {
        if (array.length==0) return init+end;
        String res = init;
        for (int i = 0; i<array.length-1; ++i) res+=array[i]+sep;
        res+=array[array.length-1]+end;
        return res;
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
    
    public static <K, V> ArrayList<K> getKeys(Map<K, V> map, List<V> values) {
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
    
    
    
    public static void setSelectedValues(List selection, JList list, DefaultListModel model) {
        List<Integer> selectedIndicies = new ArrayList<Integer>();
        for (Object s : selection) {
            int i = model.indexOf(s);
            if (i!=-1) selectedIndicies.add(i);
            if (!selectedIndicies.isEmpty()) {
                int[] res = Utils.toArray(selectedIndicies, false);
                list.setSelectedIndices(res);
            }
        }
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
    
    public static void plotProfile(String title, float[] values) {
        float[] x=new float[values.length];
        for (int i = 0; i<x.length; ++i) x[i]=i;
        new Plot(title, "coord", "value", x, values).show();
    }
    
    public static void plotProfile(String title, double[] values) {
        double[] x=new double[values.length];
        for (int i = 0; i<x.length; ++i) x[i]=i;
        new Plot(title, "coord", "value", x, values).show();
    }
    
    public static void deleteDirectory(File dir) { //recursive delete, because java's native function wants the dir to be empty to delete it
        if (dir==null || !dir.exists()) return;
        if (dir.isFile()) dir.delete();
        else {
            for (File f : dir.listFiles()) deleteDirectory(f);
            dir.delete();
        }
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
    
    public static <T> void removeDuplicates(List<T> list, boolean keepOrder) {
        Collection<T> set = keepOrder? new LinkedHashSet<T>(list) : new HashSet<T>(list);
        list.clear();
        list.addAll(set);
    }
    
    public static Comparator<StructureObject> getStructureObjectComparator() {
        return new Comparator<StructureObject>() {
            public int compare(StructureObject arg0, StructureObject arg1) { // timePoint, structureIdx, idx
                int comp = Integer.compare(arg0.getTimePoint(), arg1.getTimePoint());
                if (comp==0) {
                    comp = Integer.compare(arg0.getStructureIdx(), arg1.getStructureIdx());
                    if (comp==0) {
                        if (arg0.getParent()!=null && arg1.getParent()!=null) {
                            comp = compare(arg0.getParent(), arg1.getParent());
                            if (comp!=0) return comp;
                        }
                        return Integer.compare(arg0.getIdx(), arg1.getIdx());
                    }
                    else return comp;
                } else return comp;
            }
        };
    }

}
