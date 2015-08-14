/*
 * Copyright (C) 2015 nasique
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
package dataStructure.objects;

import java.util.ArrayList;
import java.util.Collections;

/**
 *
 * @author nasique
 */
public class StructureObjectUtils {
    /**
     * 
     * @param root
     * @param pathToRoot array of structure indices, in hierachical order, from the root to the given structure
     * @return all the objects of the last structure of the path
     */
    public static ArrayList<StructureObject> getAllObjects(StructureObject root, int[] pathToRoot) {
        if (pathToRoot.length==0) return new ArrayList<StructureObject>(0);
        ArrayList<StructureObject> currentChildren = new ArrayList<StructureObject>(root.getChildObjects(pathToRoot[0]).length);
        Collections.addAll(currentChildren, root.getChildObjects(pathToRoot[0]));
        for (int i = 1; i<pathToRoot.length; ++i) currentChildren = getAllChildren(currentChildren, pathToRoot[i]);
        return currentChildren;
    }
    
    private static ArrayList<StructureObject> getAllChildren(ArrayList<StructureObject> parents, int childrenStructureIdx) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        for (StructureObject parent : parents) Collections.addAll(res, parent.getChildObjects(childrenStructureIdx));
        return res;
    } 
    
    public static ArrayList<StructureObject> getAllParentObjects(StructureObject root, int[] pathToRoot) {
        if (pathToRoot.length==0) return new ArrayList<StructureObject>(0);
        else if (pathToRoot.length==1) {
            ArrayList<StructureObject> res = new ArrayList<StructureObject>(1);
            res.add(root);
            return res;
        } else {
            int[] pathToRoot2 = new int[pathToRoot.length-1];
            System.arraycopy(pathToRoot, 0, pathToRoot2, 0, pathToRoot2.length);
            return getAllObjects(root, pathToRoot2);
        }
    }
    
    
}
