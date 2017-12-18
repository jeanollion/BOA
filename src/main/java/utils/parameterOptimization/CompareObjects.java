/*
 * Copyright (C) 2017 jollion
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
package utils.parameterOptimization;

import static boa.gui.DBUtil.searchForLocalDir;
import core.Processor;
import core.Task;
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MasterDAOFactory;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import measurement.GeometricalMeasurements;
import org.slf4j.LoggerFactory;
import plugins.PluginFactory;
import utils.JSONUtils;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;

/**
 *
 * @author jollion
 */
public class CompareObjects {
    public static final org.slf4j.Logger logger = LoggerFactory.getLogger(CompareObjects.class);
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        String ref = "fluo171204_WT_750ms_paramOptimizationRef";
        String db = "fluo171204_WT_750ms_paramOptimization";
        int structureIdx = 2;
        double distCC = 0.1;
        String unshureSel = "unshureMutations";
        CompareObjects comp = new CompareObjects(ref, db, structureIdx, distCC, unshureSel);
        //comp.processPositions(0, 1, 2, 3, 4);
        comp.runOnPositions(0, 1, 2, 3, 4);
        logger.debug("false positive: {}, false negative: {}", comp.getFalsePositive(), comp.getFalseNegative());
        comp.close();
    }
    final MasterDAO dbRef, db;
    final double distCCThldSq;
    int falsePositive, falseNegative;
    final Object countLock = new Object();
    Selection unshureObjects;
    final int structureIdx, parentStructureIdx;
    public CompareObjects(String refXP, String xp, int structureIdx, double distCCThld, String unshureSelectionName) {
        dbRef = new Task(refXP).getDB();
        dbRef.setReadOnly(true);
        db = new Task(xp).getDB();
        this.structureIdx=structureIdx;
        this.parentStructureIdx = db.getExperiment().getStructure(structureIdx).getParentStructure();
        this.distCCThldSq=distCCThld*distCCThld;
        if (unshureSelectionName!=null) {
            this.unshureObjects = dbRef.getSelectionDAO().getOrCreate(unshureSelectionName, false);
            logger.debug("unshure objects: {}", unshureObjects.count());
        }
    }
    public void close() {
        dbRef.clearCache();
        db.clearCache();
    }
    public CompareObjects setConfig(Experiment config) {
        db.getExperiment().getStructure(structureIdx).setContentFrom(config.getStructure(structureIdx));
        return this;
    }
    public CompareObjects setConfig(String configFile) {
        if (configFile!=null) {  // import config file
            try {
                Experiment newCfg = new Experiment();
                RandomAccessFile rdn = new RandomAccessFile(configFile, "r");
                String xpString = rdn.readLine();
                newCfg.initFromJSONEntry(JSONUtils.parse(xpString));
                return setConfig(newCfg);
            } catch (IOException ex) {
                
            }
        }
        return this;
    }
    public void processPositions(int... positions) {
        String[] pos = new String[positions.length];
        String[] allPos = db.getExperiment().getPositionsAsString();
        int count = 0;
        for (int p : positions) pos[count++] = allPos[p];
        processPositions(pos);
    }
    public void processPositions(String... positions) {
        if (db.isReadOnly()) throw new RuntimeException("DB "+db.getDBName()+" is in read only mode, cannot process");
        for (String p: positions) Processor.processAndTrackStructures(db.getDao(p), true, false, structureIdx);
    }
    public void runOnAllPositions() {
        runOnPositions(db.getExperiment().getPositionsAsString());
    }
    public void runOnPositions(int... positions) {
        String[] pos = new String[positions.length];
        String[] allPos = db.getExperiment().getPositionsAsString();
        int count = 0;
        for (int p : positions) pos[count++] = allPos[p];
        runOnPositions(pos);
    }
    public void runOnPositions(String... positions) {
        ThreadRunner.execute(positions, true, (String position, int idx) -> {
            runPosition(position);
        }, null);
    }

    public int getFalsePositive() {
        return falsePositive;
    }

    public int getFalseNegative() {
        return falseNegative;
    }
    
    protected void runPosition(String positionName) {
        Map<StructureObject, List<StructureObject>> parentTrackRef = StructureObjectUtils.getAllTracks(dbRef.getDao(positionName).getRoots(), parentStructureIdx);
        Map<StructureObject, List<StructureObject>> parentTrack = StructureObjectUtils.getAllTracks(db.getDao(positionName).getRoots(), parentStructureIdx);
        if (parentTrackRef.size()!=parentTrack.size()) throw new RuntimeException("DB & Ref don't have same number of parents tracks @ position: "+positionName);
        List<StructureObject> thRef = new ArrayList<>(parentTrackRef.keySet());
        Collections.sort(thRef);
        List<StructureObject> th = new ArrayList<>(parentTrack.keySet());
        Collections.sort(th);
        int[] falsePositiveAndNegative = new int[2];
        for (int pIdx = 0; pIdx<parentTrackRef.size(); ++pIdx) {
            List<StructureObject> refTrack = parentTrackRef.get(thRef.get(pIdx));
            List<StructureObject> track = parentTrack.get(th.get(pIdx));
            if (refTrack.size()!=track.size()) throw new RuntimeException("DB & Ref track don't have same length @ position: "+positionName);
            for (int frame = 0; frame<refTrack.size(); ++frame) compareObjects(refTrack.get(frame), track.get(frame), falsePositiveAndNegative);
        }
        synchronized(countLock) {
            falsePositive+=falsePositiveAndNegative[0];
            falseNegative+=falsePositiveAndNegative[1];
        }
    }
    protected void compareObjects(StructureObject pRef, StructureObject p, int[] fpn) {
        List<StructureObject> objects = p.getChildren(structureIdx);
        List<StructureObject> ref = new ArrayList<>(pRef.getChildren(structureIdx));
        //if (!objects.isEmpty() || !ref.isEmpty() ) logger.debug("compare objects: {}={} & {}={}", pRef, ref.size(), p, objects.size());
        for (StructureObject o : objects) {
            if (ref.isEmpty()) { // false positive spot
                //logger.debug("no assignment for: {}", o);
                ++fpn[0];
            } else { // simple assign by decreasing distCC
                StructureObject closest = Collections.min(ref, (o1, o2)->Double.compare(GeometricalMeasurements.getDistanceSquare(o1.getObject(), o.getObject()), GeometricalMeasurements.getDistanceSquare(o2.getObject(), o.getObject())));
                double d = GeometricalMeasurements.getDistanceSquare(closest.getObject(), o.getObject());
                if (d<=distCCThldSq) { // assigned
                    ref.remove(closest);
                    //logger.debug("{} assigned to {}, by distance: {}", o, closest, d);
                } 
                else  {
                    //logger.debug("no assignment for: {}", o);
                    ++fpn[0];
                }
            }
        }
        if (!ref.isEmpty()) { // false negative
            if (unshureObjects!=null) ref.removeIf(o->unshureObjects.contains(o));
            fpn[1]+=ref.size();
            //logger.debug("remaining ref objects: {}", ref);
        }
    }
}
