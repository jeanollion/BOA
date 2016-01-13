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
package dataStructure.objects;

import dataStructure.configuration.Experiment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public class BasicObjectDAO implements ObjectDAO {
    final MasterDAO masterDAO;
    StructureObject[] rootTrack;
    final String fieldName;
    public BasicObjectDAO(MasterDAO masterDAO, ArrayList<StructureObject> rootTrack) {
        this.masterDAO=masterDAO;
        if (rootTrack.isEmpty()) throw new IllegalArgumentException("root track should not be empty");
        this.rootTrack=rootTrack.toArray(new StructureObject[0]);
        this.fieldName=this.rootTrack[0].getFieldName();
    }
    
    public BasicObjectDAO(MasterDAO masterDAO, String fieldName) {
        this.masterDAO=masterDAO;
        this.fieldName= fieldName;
    }
    
    public Experiment getExperiment() {
        return masterDAO.getExperiment();
    }

    public String getFieldName() {
        return fieldName;
    }

    public void clearCache() {
        // no cache..
    }

    public ArrayList<StructureObject> getChildren(StructureObject parent, int structureIdx) {
        return this.rootTrack[parent.getTimePoint()].getChildren(structureIdx);
    }

    public void deleteChildren(StructureObject parent, int structureIdx) {
        parent.setChildren(null, structureIdx);
    }

    public void deleteObjectsByStructureIdx(int... structures) {
        for (int s : structures) deleteObjectByStructureIdx(s);
    }
    
    protected void deleteObjectByStructureIdx(int structureIdx) {
        if (structureIdx==-1) deleteAllObjects();
        int[] pathToRoot = getExperiment().getPathToRoot(structureIdx);
        if (pathToRoot.length==1) for (StructureObject r : this.rootTrack) deleteChildren(r, structureIdx);
        else {
            for (StructureObject r : this.rootTrack) {
                ArrayList<StructureObject> allParents = r.getChildren(pathToRoot[pathToRoot.length-2]);
                for (StructureObject p : allParents) deleteChildren(p, structureIdx);
            }
        }
    }

    public void deleteAllObjects() {
        this.rootTrack=null;
    }
    /**
     * 
     * @param o
     * @param deleteChildren not used in this DAO, chilren are always deleted
     */
    public void delete(StructureObject o, boolean deleteChildren) {
        if (o.getStructureIdx()==-1) rootTrack[o.getTimePoint()]=null;
        else o.getParent().getChildren(o.getStructureIdx()).remove(o);
    }

    public void delete(List<StructureObject> list, boolean deleteChildren) {
        for (StructureObject o : list) delete(o, deleteChildren);
    }

    public void store(StructureObject object, boolean updateTrackAttributes) {
        object.dao=this;
        if (object.structureIdx==-1) {
            if (rootTrack==null) rootTrack = new StructureObject[object.getTimePoint()+10];
            else if (rootTrack.length<=object.getTimePoint()) {
                StructureObject[] rtemp = new StructureObject[rootTrack.length*2];
                System.arraycopy(rootTrack, 0, rtemp, 0, rootTrack.length);
            }
            rootTrack[object.getTimePoint()] = object;
        } else {
            ArrayList<StructureObject> children = object.getParent().getChildren(object.getStructureIdx());
            if (children == null) {
                children = new ArrayList<StructureObject>();
                object.getParent().setChildren(children, object.getStructureIdx());
            } else {
                if (!children.contains(object)) children.add(object.idx, object);
            }
        }
    }

    public void store(List<StructureObject> objects, boolean updateTrackAttributes) {
        for (StructureObject o : objects) store(o, updateTrackAttributes);
    }

    public ArrayList<StructureObject> getRoots() {
        if (rootTrack!=null) return new ArrayList<StructureObject>(Arrays.asList(rootTrack));
        else return null;
    }

    public StructureObject getRoot(int timePoint) {
        return rootTrack[timePoint];
    }

    public ArrayList<StructureObject> getTrack(StructureObject trackHead) {
        if (trackHead.getStructureIdx()==-1) return getRoots();
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        res.add(trackHead);
        while(trackHead.getTimePoint()+1<rootTrack.length && rootTrack[trackHead.getTimePoint()+1]!=null) {
            if (trackHead.getNext()!=null) trackHead = trackHead.getNext();
            else { // look for next:
                ArrayList<StructureObject> candidates = rootTrack[trackHead.getTimePoint()+1].getChildren(trackHead.getStructureIdx());
                StructureObject next = null;
                for (StructureObject c : candidates) {
                    if (c.getPrevious()==trackHead) {
                        trackHead.next = c;
                        next = c;
                        break;
                    }
                }
                if (next!=null) trackHead=next;
                else return res;
            }
            res.add(trackHead);
        }
        return res;
    }

    public ArrayList<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        if (structureIdx==-1) res.add(this.rootTrack[0]);
        else {
            for (StructureObject r : rootTrack) {
                if (r!=null) {
                    ArrayList<StructureObject> candidates = r.getChildren(structureIdx);
                    for (StructureObject c : candidates) {
                        if (c.isTrackHead()) res.add(c);
                    }
                }
            }
        }
        return res;
    }

    public void upsertMeasurements(List<StructureObject> objects) {
        // measurements are stores in objects...
    }

    public void upsertMeasurement(StructureObject o) {
        // measurements are stores in objects...
    }

    public List<Measurements> getMeasurements(int structureIdx, String... measurements) {
        List<Measurements> res = new ArrayList<Measurements>();
        if (structureIdx==-1) {
            for (StructureObject r : rootTrack) {
                if (r!=null && r.getMeasurements()!=null) {
                    res.add(r.getMeasurements());
                }
            }
        } else {
            for (StructureObject r : rootTrack) {
                if (r!=null) {
                    for (StructureObject c : r.getChildren(structureIdx)) {
                        if (c.getMeasurements()!=null) res.add(c.getMeasurements());
                    }
                }
            }
        }
        return res;
    }

}
