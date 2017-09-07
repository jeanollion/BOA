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
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class BasicObjectDAO implements ObjectDAO {
    public static final Logger logger = LoggerFactory.getLogger(BasicObjectDAO.class);
    final MasterDAO masterDAO;
    Map<Integer, StructureObject> rootTrack;
    final String fieldName;
    public BasicObjectDAO(MasterDAO masterDAO, List<StructureObject> rootTrack) {
        this.masterDAO=masterDAO;
        if (rootTrack.isEmpty()) throw new IllegalArgumentException("root track should not be empty");
        this.rootTrack = new HashMap<>(rootTrack.size());
        int idx = 0;
        for (StructureObject r : rootTrack) this.rootTrack.put(r.getFrame(), r);
        this.fieldName=rootTrack.get(0).getPositionName();
    }
    
    public BasicObjectDAO(MasterDAO masterDAO, String fieldName) {
        this.masterDAO=masterDAO;
        this.fieldName= fieldName;
        this.rootTrack = new HashMap<>();
    }
    
    public Experiment getExperiment() {
        return masterDAO.getExperiment();
    }

    public String getPositionName() {
        return fieldName;
    }

    public void clearCache() {
        // no cache..
    }

    @Override
    public List<StructureObject> getChildren(StructureObject parent, int structureIdx) {
        logger.debug("try to get children of : {} from structure: {}", this, structureIdx);
        return null;
    }
    
    @Override
    public void setAllChildren(List<StructureObject> parentTrack, int childStructureIdx) {
        
    }
    
    @Override 
    public void deleteChildren(Collection<StructureObject> parents, int structureIdx) {
        for (StructureObject p : parents) deleteChildren(p, structureIdx);
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
        if (pathToRoot.length==1) for (StructureObject r : rootTrack.values()) deleteChildren(r, structureIdx);
        else {
            for (StructureObject r : rootTrack.values()) {
                List<StructureObject> allParents = r.getChildren(pathToRoot[pathToRoot.length-2]);
                for (StructureObject p : allParents) deleteChildren(p, structureIdx);
            }
        }
    }

    public void deleteAllObjects() {
        this.rootTrack.clear();
    }
    /**
     * 
     * @param o
     * @param deleteChildren not used in this DAO, chilren are always deleted
     */
    public void delete(StructureObject o, boolean deleteChildren, boolean deleteFromParent, boolean relabelSiblings) {
        if (o.getStructureIdx()==-1) rootTrack.remove(o.getFrame());
        else {
            if (o.getParent()!=null) o.getParent().getChildren(o.getStructureIdx()).remove(o);
            if (relabelSiblings && o.getParent()!=null) o.getParent().relabelChildren(o.getStructureIdx());
        }
    }

    public void delete(Collection<StructureObject> list, boolean deleteChildren, boolean deleteFromParent, boolean relabelSiblings) {
        for (StructureObject o : list) delete(o, deleteChildren, deleteFromParent, relabelSiblings);
    }

    public void store(StructureObject object) {
        object.dao=this;
        if (object.structureIdx==-1) {
            rootTrack.put(object.getFrame(), object);
        } else {
            List<StructureObject> children = object.getParent().getChildren(object.getStructureIdx());
            if (children == null) {
                children = new ArrayList<StructureObject>();
                object.getParent().setChildren(children, object.getStructureIdx());
            } else {
                if (!children.contains(object)) children.add(object.idx, object);
            }
        }
    }

    public void store(Collection<StructureObject> objects) {
        for (StructureObject o : objects) store(o);
    }

    public List<StructureObject> getRoots() {
        List<StructureObject> res = new ArrayList<>(this.rootTrack.values());
        Collections.sort(res);
        return res;
    }

    public StructureObject getRoot(int timePoint) {
        return rootTrack.get(timePoint);
    }

    public List<StructureObject> getTrack(StructureObject trackHead) {
        if (trackHead.getStructureIdx()==-1) return getRoots();
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        res.add(trackHead);
        while(rootTrack.get(trackHead.getFrame()+1)!=null) {
            if (trackHead.getNext()!=null) trackHead = trackHead.getNext();
            else { // look for next:
                List<StructureObject> candidates = rootTrack.get(trackHead.getFrame()+1).getChildren(trackHead.getStructureIdx());
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

    public List<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx) {
        ArrayList<StructureObject> res = new ArrayList<StructureObject>();
        if (structureIdx==-1) res.add(this.rootTrack.get(0));
        else {
            for (StructureObject r : getRoots()) {
                if (r!=null) {
                    List<StructureObject> candidates = r.getChildren(structureIdx);
                    for (StructureObject c : candidates) {
                        if (c.isTrackHead()) res.add(c);
                    }
                }
            }
        }
        return res;
    }

    public void upsertMeasurements(Collection<StructureObject> objects) {
        // measurements are stored in objects...
    }

    public void upsertMeasurement(StructureObject o) {
        // measurements are stored in objects...
    }
    
    public void upsertModifiedMeasurements() {
        // measurements are stored in objects...
    }

    public List<Measurements> getMeasurements(int structureIdx, String... measurements) {
        List<Measurements> res = new ArrayList<Measurements>();
        if (structureIdx==-1) {
            for (StructureObject r : getRoots()) {
                if (r!=null && r.getMeasurements()!=null) {
                    res.add(r.getMeasurements());
                }
            }
        } else {
            for (StructureObject r : getRoots()) {
                if (r!=null) {
                    for (StructureObject c : r.getChildren(structureIdx)) {
                        if (c.getMeasurements()!=null) res.add(c.getMeasurements());
                    }
                }
            }
        }
        return res;
    }
    @Override
    public void deleteAllMeasurements() {
        int structureCount = getExperiment().getStructureCount();
        for (StructureObject root : rootTrack.values()) {
            for (int sIdx = 0; sIdx<structureCount; ++sIdx) {
                for(StructureObject o : root.getChildren(sIdx)) {
                    o.measurements=null;
                }
            }
        } 
    }

    @Override
    public MasterDAO getMasterDAO() {
        return this.masterDAO;
    }

    @Override
    public void setRoots(List<StructureObject> roots) {
        for (int i = 0; i<roots.size(); ++i) rootTrack.put(i, roots.get(i));
    }

    @Override
    public StructureObject getById(String parentTrackHeadId, int structureIdx, int frame, String id) {
        if (frame>=0) {
            for (StructureObject o : this.getRoot(frame).getChildren(structureIdx)) if (o.id.equals(id)) return o;
            return null;
        } else if (parentTrackHeadId!=null) {
            return null;
            //throw new UnsupportedOperationException("not supported");
        }
        return null;
        //throw new UnsupportedOperationException("not supported");
    }

    @Override
    public Measurements getMeasurements(StructureObject o) {
        return o.measurements;
    }

    

    
}
