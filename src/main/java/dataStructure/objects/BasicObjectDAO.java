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
import utils.SmallArray;

/**
 *
 * @author jollion
 */
public class BasicObjectDAO implements ObjectDAO {
    final MasterDAO masterDAO;
    SmallArray<StructureObject> rootTrack;
    final String fieldName;
    public BasicObjectDAO(MasterDAO masterDAO, ArrayList<StructureObject> rootTrack) {
        this.masterDAO=masterDAO;
        if (rootTrack.isEmpty()) throw new IllegalArgumentException("root track should not be empty");
        this.rootTrack = new SmallArray<StructureObject>(rootTrack.size());
        int idx = 0;
        for (StructureObject r : rootTrack) this.rootTrack.setQuick(r, idx++);
        this.fieldName=rootTrack.get(0).getFieldName();
    }
    
    public BasicObjectDAO(MasterDAO masterDAO, String fieldName) {
        this.masterDAO=masterDAO;
        this.fieldName= fieldName;
        this.rootTrack = new SmallArray<StructureObject>();
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
        return this.rootTrack.get(parent.getTimePoint()).getChildren(structureIdx);
    }

    public void deleteChildren(StructureObject parent, int structureIdx) {
        parent.setChildren(new ArrayList<StructureObject>(0), structureIdx);
    }

    public void deleteObjectsByStructureIdx(int... structures) {
        for (int s : structures) deleteObjectByStructureIdx(s);
    }
    
    protected void deleteObjectByStructureIdx(int structureIdx) {
        if (structureIdx==-1) deleteAllObjects();
        int[] pathToRoot = getExperiment().getPathToRoot(structureIdx);
        if (pathToRoot.length==1) for (StructureObject r : rootTrack.getObjectsQuick()) deleteChildren(r, structureIdx);
        else {
            for (StructureObject r : rootTrack.getObjectsQuick()) {
                ArrayList<StructureObject> allParents = r.getChildren(pathToRoot[pathToRoot.length-2]);
                for (StructureObject p : allParents) deleteChildren(p, structureIdx);
            }
        }
    }

    public void deleteAllObjects() {
        this.rootTrack.flush();
    }
    /**
     * 
     * @param o
     * @param deleteChildren not used in this DAO, chilren are always deleted
     */
    public void delete(StructureObject o, boolean deleteChildren) {
        if (o.getStructureIdx()==-1) rootTrack.set(null, o.getTimePoint());
        else o.getParent().getChildren(o.getStructureIdx()).remove(o);
    }

    public void delete(List<StructureObject> list, boolean deleteChildren) {
        for (StructureObject o : list) delete(o, deleteChildren);
    }

    public void store(StructureObject object, boolean updateTrackAttributes) {
        object.dao=this;
        if (object.structureIdx==-1) {
            rootTrack.set(object, object.getTimePoint());
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
        int needToExtend = -1;
        for (StructureObject o : objects) if (o.getStructureIdx()==-1 && o.getTimePoint()>needToExtend) needToExtend = o.getTimePoint();
        if (needToExtend>0) rootTrack.extend(needToExtend);
        for (StructureObject o : objects) store(o, updateTrackAttributes);
    }

    public ArrayList<StructureObject> getRoots() {
        return this.rootTrack.getObjectsQuick();
    }

    public StructureObject getRoot(int timePoint) {
        return rootTrack.get(timePoint);
    }

    public ArrayList<StructureObject> getTrack(StructureObject trackHead) {
        if (trackHead.getStructureIdx()==-1) return getRoots();
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        res.add(trackHead);
        int max = rootTrack.getBucketSize();
        while(trackHead.getTimePoint()+1<max && rootTrack.get(trackHead.getTimePoint()+1)!=null) {
            if (trackHead.getNext()!=null) trackHead = trackHead.getNext();
            else { // look for next:
                ArrayList<StructureObject> candidates = rootTrack.getQuick(trackHead.getTimePoint()+1).getChildren(trackHead.getStructureIdx());
                StructureObject next = null;
                for (StructureObject c : candidates) {
                    if (c.getPrevious()==trackHead) {
                        trackHead.setNext(c);
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
        if (structureIdx==-1) res.add(this.rootTrack.get(0));
        else {
            for (StructureObject r : rootTrack.getObjectsQuick()) {
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
        // measurements are stored in objects...
    }

    public void upsertMeasurement(StructureObject o) {
        // measurements are stored in objects...
    }

    public List<Measurements> getMeasurements(int structureIdx, String... measurements) {
        List<Measurements> res = new ArrayList<Measurements>();
        if (structureIdx==-1) {
            for (StructureObject r : rootTrack.getObjectsQuick()) {
                if (r!=null && r.getMeasurements()!=null) {
                    res.add(r.getMeasurements());
                }
            }
        } else {
            for (StructureObject r : rootTrack.getObjectsQuick()) {
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
