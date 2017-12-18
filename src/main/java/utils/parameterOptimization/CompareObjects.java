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
import java.io.File;
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
import utils.FileIO;
import utils.JSONUtils;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

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
        String configFolder = "/data/Images/Fluo/fluo171204_WT_750ms_paramOptimization/configSet/";
        String outputFile = "/data/Images/Fluo/fluo171204_WT_750ms_paramOptimization/ParamOptimizationOutput.txt";
        int structureIdx = 2;
        double distCC = 0.1;
        String unshureSel = "unshureMutations";
        CompareObjects comp = new CompareObjects(ref, db, structureIdx, distCC, unshureSel);
        comp.setOutputFile(outputFile, false);
        //comp.scanConfigurationFolderAndRunAndCount(configFolder, 0);
        comp.enableSelection();
        comp.setConfig(configFolder + "base.txt");
        comp.processPositions(0);
        comp.runOnPositions(0);
        logger.debug("false positive: {}, false negative: {}", comp.getFalsePositive(), comp.getFalseNegative());
        comp.close();
    }
    final MasterDAO dbRef, db;
    final double distCCThldSq;
    int falsePositive, falseNegative;
    final Object countLock = new Object();
    Selection unshureObjects, fp, fn;
    final int structureIdx, parentStructureIdx;
    RandomAccessFile output;
    String configName;
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
    public void enableSelection() {
        fp= db.getSelectionDAO().getOrCreate("falsePositives", true);
        fn = db.getSelectionDAO().getOrCreate("falseNegatives", true);
    }
    public void scanConfigurationFolderAndRunAndCount(String folder, int... positions) {
        if (output==null) throw new IllegalArgumentException("No output file set");
        File[] configs = new File(folder).listFiles(f->f.getName().endsWith(".txt"));
        for (File f : configs) {
            if (setConfig(f.getAbsolutePath())) {
                logger.debug("config: {}", f.getAbsolutePath());
                processPositions(positions);
                runOnPositions(positions);
                appendToOutputFile();
            }
        }
    }
    public void close() {
        dbRef.clearCache();
        db.clearCache();
        if (output!=null) {
            try {
                output.close();
            } catch (IOException ex) { }
        }
    }
    public void setOutputFile(String file, boolean append) {
        try {
            this.output = new RandomAccessFile(file, "rw");
            if (!append) FileIO.clearRAF(output);
        } catch (IOException ex) {
            throw new RuntimeException("Could not create output file @ "+file);
        }
    }
    public void appendToOutputFile() {
        if (output==null) throw new RuntimeException("No output file set");
        StringBuilder sb = new StringBuilder();
        sb.append("False Positive/Negative: [");
        sb.append(this.falsePositive);
        sb.append("; ");
        sb.append(this.falseNegative);
        sb.append("]");
        if (configName!=null) {
            sb.append(" for Config: ");
            sb.append(configName);
        }
        try {
            FileIO.write(output, sb.toString(), true);
        } catch (IOException ex) {
            throw new RuntimeException("Could not append to output file "+ex.getLocalizedMessage());
        }
    }
    public boolean setConfig(Experiment config) {
        db.getExperiment().getStructure(structureIdx).setContentFrom(config.getStructure(structureIdx));
        return true;
    }
    public boolean setConfig(String configFile) {
        if (configFile!=null) {  // import config file
            try {
                Experiment newCfg = new Experiment();
                RandomAccessFile rdn = new RandomAccessFile(configFile, "r");
                String xpString = rdn.readLine();
                newCfg.initFromJSONEntry(JSONUtils.parse(xpString));
                this.configName = Utils.removeExtension(new File(configFile).getName());
                return setConfig(newCfg);
            } catch (IOException ex) {
                return false;
            }
        }
        return false;
    }
    public void processPositions(int... positions) {
        if (positions.length==0) processPositions(db.getExperiment().getPositionsAsString());
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
        if (positions.length==0) runOnAllPositions();
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
        if (this.fp!=null) db.getSelectionDAO().store(fp);
        if (this.fn!=null) db.getSelectionDAO().store(fn);
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
        List<StructureObject> fpObjects = this.fp!=null ? new ArrayList<>() : null;
        List<StructureObject> fnObjects = this.fn!=null ? new ArrayList<>() : null;
        for (int pIdx = 0; pIdx<parentTrackRef.size(); ++pIdx) {
            List<StructureObject> refTrack = parentTrackRef.get(thRef.get(pIdx));
            List<StructureObject> track = parentTrack.get(th.get(pIdx));
            if (refTrack.size()!=track.size()) throw new RuntimeException("DB & Ref track don't have same length @ position: "+positionName);
            for (int frame = 0; frame<refTrack.size(); ++frame) compareObjects(refTrack.get(frame), track.get(frame), falsePositiveAndNegative, fpObjects, fnObjects);
        }
        synchronized(countLock) {
            falsePositive+=falsePositiveAndNegative[0];
            falseNegative+=falsePositiveAndNegative[1];
            if (fnObjects!=null && !fnObjects.isEmpty()) {
                db.getDao(positionName).store(fnObjects);
                fn.addElements(fnObjects);
            }
            if (fpObjects!=null && !fpObjects.isEmpty()) fp.addElements(fpObjects);
        }
    }
    protected void compareObjects(StructureObject pRef, StructureObject p, int[] fpn, List<StructureObject> fp, List<StructureObject> fn) {
        List<StructureObject> objects = p.getChildren(structureIdx);
        List<StructureObject> ref = new ArrayList<>(pRef.getChildren(structureIdx));
        //if (!objects.isEmpty() || !ref.isEmpty() ) logger.debug("compare objects: {}={} & {}={}", pRef, ref.size(), p, objects.size());
        for (StructureObject o : objects) {
            if (ref.isEmpty()) { // false positive spot
                //logger.debug("no assignment for: {}", o);
                ++fpn[0];
                if (fp!=null) fp.add(o);
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
                    if (fp!=null) fp.add(o);
                }
            }
        }
        if (!ref.isEmpty()) { // false negative
            if (unshureObjects!=null) ref.removeIf(o->unshureObjects.contains(o));
            fpn[1]+=ref.size();
            if (fn!=null) { // duplicate objects and add to corresponding parent
                List<StructureObject> fnToAdd = Utils.transform(ref, o->o.duplicate());
                int count = objects.size();
                for (StructureObject o : fnToAdd) {
                    o.setIdx(count++);
                    o.setParent(p);
                }
                fn.addAll(fnToAdd);
            }
            //logger.debug("remaining ref objects: {}", ref);
        }
    }
}
