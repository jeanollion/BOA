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
package core;

import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObject.TrackFlag;
import java.util.ArrayList;
import java.util.Iterator;

/**
 *
 * @author jollion
 */
public class TrackCorrectionUtils {
    public ArrayList<StructureObject> subsetMergedToErase(ArrayList<? extends StructureObject> objects) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(objects.size());
        Iterator<? extends StructureObject> it = objects.iterator();
        while (it.hasNext()) {
            StructureObject o = it.next();
            if (o.getParent()==null) {
                res.add(o);
                it.remove();
            }
        }
        return res;
    }
    public ArrayList<StructureObject> subsetMergedToKeep(ArrayList<? extends StructureObject> objects) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(objects.size());
        Iterator<? extends StructureObject> it = objects.iterator();
        while (it.hasNext()) {
            StructureObject o = it.next();
            if (o.getParent()!=null && o.getTrackFlag().equals(TrackFlag.correctionMerge)) {
                res.add(o);
                it.remove();
            }
        }
        return res;
    }
    public ArrayList<StructureObject> subsetSplit(ArrayList<? extends StructureObject> objects) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(objects.size());
        Iterator<? extends StructureObject> it = objects.iterator();
        while (it.hasNext()) {
            StructureObject o = it.next();
            if (o.getTrackFlag().equals(TrackFlag.correctionSplit)) {
                res.add(o);
                it.remove();
            }
        }
        return res;
    } 
    public ArrayList<StructureObject> subsetSplitNew(ArrayList<? extends StructureObject> objects) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(objects.size());
        Iterator<? extends StructureObject> it = objects.iterator();
        while (it.hasNext()) {
            StructureObject o = it.next();
            if (o.getTrackFlag().equals(TrackFlag.correctionSplitNew)) {
                res.add(o);
                it.remove();
            }
        }
        return res;
    }
    public ArrayList<StructureObject> subsetSplitNext(ArrayList<? extends StructureObject> objects) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>(objects.size());
        Iterator<? extends StructureObject> it = objects.iterator();
        while (it.hasNext()) {
            StructureObject o = it.next();
            if (o.getTrackFlag()==null && o.getPrevious()!=null && o.getPrevious().getTrackFlag().equals(TrackFlag.correctionSplitNew)) {
                res.add(o);
                it.remove();
            }
        }
        return res;
    } 
}
