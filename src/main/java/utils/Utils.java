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

import de.caluga.morphium.Morphium;
import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComboBox;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;

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
    
    
    public static String formatInteger(int paddingSize, int number) {
        return String.format(Locale.US, "%0" + paddingSize + "d", number);
    }
    
    public static int[] toArray(ArrayList<Integer> arrayList, boolean reverseOrder) {
        int[] res=new int[arrayList.size()];
        if (reverseOrder) {
            int idx = res.length-1;
            for (int s : arrayList) res[idx--] = s;
        } else for (int i = 0; i<res.length; ++i) res[i] = arrayList.get(i);
        return res;
    }
    
    public static<T> ArrayList<T> reverseOrder(ArrayList<T> arrayList) {
        ArrayList<T> res = new ArrayList<T>(arrayList.size());
        for (int i = arrayList.size()-1; i>=0; --i) res.add(arrayList.get(i));
        return res;
    }
    
    public static void addHorizontalScrollBar(JComboBox box) {
        if (box.getItemCount() == 0) return;
        Object comp = box.getUI().getAccessibleChild(box, 0);
        if (!(comp instanceof JPopupMenu)) {
            return;
        }
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
}
