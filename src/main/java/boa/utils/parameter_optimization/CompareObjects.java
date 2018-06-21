/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.utils.parameter_optimization;

import static boa.ui.DBUtil.searchForLocalDir;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.configuration.parameters.PreFilterSequence;
import boa.core.Processor;
import boa.core.Task;
import boa.configuration.experiment.Experiment;
import boa.configuration.parameters.TrackPreFilterSequence;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.MasterDAOFactory;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import boa.measurement.GeometricalMeasurements;
import org.slf4j.LoggerFactory;
import boa.plugins.PluginFactory;
import boa.plugins.TrackParametrizable;
import boa.plugins.TrackParametrizable.TrackParametrizer;
import boa.plugins.plugins.segmenters.MutationSegmenter;
import boa.utils.ArrayUtil;
import boa.utils.FileIO;
import boa.utils.FileIO.TextFile;
import boa.utils.HashMapGetCreate;
import boa.utils.JSONUtils;
import boa.utils.Pair;
import boa.utils.ThreadRunner;
import boa.utils.ThreadRunner.ThreadAction;
import boa.utils.Utils;
import java.util.HashMap;
import java.util.Map.Entry;
import boa.plugins.ProcessingPipeline;

/**
 *
 * @author jollion
 */
public class CompareObjects {
    public static final org.slf4j.Logger logger = LoggerFactory.getLogger(CompareObjects.class);
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        String ref = "fluo171204_WT_750ms_paramOptimizationRef";
        String db = "fluo171204_WT_750ms_paramOptimization";
        String configFolder = "/data/Images/Fluo/fluo171204_WT_750ms_paramOptimization/configSet/";
        String outputFile = "/data/Images/Fluo/fluo171204_WT_750ms_paramOptimization/ParamOptimizationOutput.txt";
        int structureIdx = 2;
        double distCC = 0.1; // TODO test influence of distCC with only base
        String unshureSel = "unshureMutations";
        CompareObjects comp = new CompareObjects(ref, db, structureIdx, distCC, unshureSel, true);
        comp.setOutputFile(outputFile, true);
        comp.scanConfigurationFolderAndRunAndCount(configFolder);
        //comp.setConfigAndRun(configFolder + "q235.json", true, 0);
        //comp.run(true, true, 0);
    }

    final MasterDAO dbRef, db;
    final double distCCThldSq;
    int falsePositive, falseNegative, totalRef;
    final Object countLock = new Object();
    Selection unshureObjects, fp, fn, fpRef, fnRef;
    final int structureIdx, parentStructureIdx;
    TextFile output;
    String configName;
    public CompareObjects(String refXP, String xp, int structureIdx, double distCCThld, String unshureSelectionName, boolean allowModifyRefXP) {
        dbRef = new Task(refXP).getDB();
        dbRef.setReadOnly(!allowModifyRefXP);
        db = new Task(xp).getDB();
        this.structureIdx=structureIdx;
        this.parentStructureIdx = db.getExperiment().getStructure(structureIdx).getParentStructure();
        this.distCCThldSq=distCCThld*distCCThld;
        if (unshureSelectionName!=null) {
            this.unshureObjects = dbRef.getSelectionDAO().getOrCreate(unshureSelectionName, false);
            logger.debug("unshure objects: {}", unshureObjects.count());
        }
    }
    public void enableSelection(boolean eraseIfExisting) {
        if (db.isReadOnly()) throw new IllegalArgumentException("DB :"+db.getDBName()+" is in read only mode, cannot enable selection");
        fp= db.getSelectionDAO().getOrCreate("falsePositives", eraseIfExisting);
        fn = db.getSelectionDAO().getOrCreate("falseNegatives", false);
        if (eraseIfExisting) eraseSelection(fn);
    }
    public void enableRefSelection(boolean eraseIfExisting) {
        if (dbRef.isReadOnly()) throw new IllegalArgumentException("Ref DB :"+dbRef.getDBName()+" is in read only mode, cannot enable selection");
        fpRef= dbRef.getSelectionDAO().getOrCreate("falsePositives", false);
        if (eraseIfExisting) eraseSelection(fpRef);
        fnRef= dbRef.getSelectionDAO().getOrCreate("falseNegatives", eraseIfExisting);
    }
    public void resetCounts() {
        falseNegative=0;
        falsePositive=0;
        totalRef = 0;
        if (fp!=null) fp.clear();
        if (fn!=null) eraseSelection(fn);
        if (fpRef!=null) eraseSelection(fpRef);
        if (fnRef!=null) fnRef.clear();
    }
    private void eraseSelection(Selection selection) {
        if (selection==null) return;
        for (String position : selection.getAllPositions()) selection.getMasterDAO().getDao(position).delete(selection.getElements(position), true, true, true); // remove objects that were created from last run
        selection.clear();
    }
    public void setConfigAndRun(String confFile, boolean eraseSelections, int... positions) {
        if (dbRef.isReadOnly()) throw new IllegalArgumentException("Ref DB :"+dbRef.getDBName()+" is in read only mode, cannot enable selection");
        setConfig(confFile);
        run(eraseSelections, true, positions);
    }
    public void run(boolean eraseSelections, boolean process, int... positions) {
        enableSelection(eraseSelections);
        enableRefSelection(eraseSelections);
        if (process) processPositions(positions);
        comparePositions(positions);
    }
    
    public int getUnshureSpotsCount(int... positions) {
        if (unshureObjects==null) return 0;
        if (positions.length==0) return unshureObjects.count();
        int count = 0;
        for (int pos : positions) count+=unshureObjects.count(dbRef.getExperiment().getPosition(pos).getName());
        return count;
    }
    public void scanConfigurationFolderAndRunAndCount(String folder, int... positions) {
        if (output==null) throw new IllegalArgumentException("No output file set");
        if (db.isReadOnly()) throw new RuntimeException("DB could not be locked");
        File[] configs = new File(folder).listFiles(f->f.getName().endsWith(".json"));
        boolean first = true;
        for (File f : configs) {
            if (setConfig(f.getAbsolutePath())) {
                logger.debug("config: {}", f.getAbsolutePath());
                resetCounts();
                processPositions(positions);
                runOnPositions(positions);
                if (first) {
                    appendRefCount();
                    first = false;
                }
                appendToOutputFile();
            }
        }
        close();
    }
    public void close() {
        dbRef.clearCache();
        db.clearCache();
        if (output!=null) output.close();
    }
    public void setOutputFile(String file, boolean append) {
        this.output = new TextFile(file, true, true);
        if (!append) output.clear();
        if (!output.isValid()) throw new RuntimeException("Could not create output file @ "+file);
    }
    public void appendRefCount() {
        if (output==null) throw new RuntimeException("No output file set");
        StringBuilder sb = new StringBuilder();
        sb.append("Total reference objects: ");
        sb.append(totalRef);
        sb.append(" unshure objects: ");
        sb.append(unshureObjects==null ? 0 : unshureObjects.count());
        output.write(sb.toString(), true);
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
        output.write(sb.toString(), true);
    }
    public boolean setConfig(Experiment config) {
        db.getExperiment().getStructure(structureIdx).setContentFrom(config.getStructure(structureIdx));
        db.updateExperiment();
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
    public void comparePositions(int... positions) {
        runOnPositions(positions);
        logger.debug("false positive: {}, false negative: {}, total: {} unshure: {}", getFalsePositive(), getFalseNegative(), totalRef, getUnshureSpotsCount(positions) );
        close();
    }
    public void runOnPositions(int... positions) {
        if (positions.length==0) {
            runOnAllPositions();
            return;
        }
        String[] pos = new String[positions.length];
        String[] allPos = db.getExperiment().getPositionsAsString();
        int count = 0;
        for (int p : positions) pos[count++] = allPos[p];
        runOnPositions(pos);
    }
    public void runOnPositions(String... positions) {
        ThreadRunner.execute(positions, true, (String position, int idx) -> {
            //setQuality(position); // specific to mutations!! 
            runPosition(position);
        }, null);

        if (this.fp!=null) db.getSelectionDAO().store(fp);
        if (this.fn!=null) db.getSelectionDAO().store(fn);
        if (this.fpRef!=null) dbRef.getSelectionDAO().store(fpRef);
        if (this.fnRef!=null) dbRef.getSelectionDAO().store(fnRef);
    }

    public int getFalsePositive() {
        return falsePositive;
    }

    public int getFalseNegative() {
        return falseNegative;
    }
    protected void setQuality(String positionName) { // specific for mutation segmenter !! get config from current db
        Map<StructureObject, List<StructureObject>> parentTrackRef = StructureObjectUtils.getAllTracks(dbRef.getDao(positionName).getRoots(), parentStructureIdx);
        ProcessingPipeline ps = db.getExperiment().getStructure(structureIdx).getProcessingScheme();
        TrackPreFilterSequence tpf = ps.getTrackPreFilters(true);
        MutationSegmenter seg = (MutationSegmenter)ps.getSegmenter();
        Map<StructureObject, TrackParametrizer> pthMapParametrizer = new HashMap<>();
        for (Entry<StructureObject, List<StructureObject>> e : parentTrackRef.entrySet()) {
            tpf.filter(structureIdx, e.getValue());
            pthMapParametrizer.put(e.getKey(), TrackParametrizable.getTrackParametrizer(structureIdx, e.getValue(), ps.getSegmenter()));
        }
        pthMapParametrizer.entrySet().removeIf(e->e.getValue()==null);
        for (StructureObject parent : Utils.flattenMap(parentTrackRef)) {
            RegionPopulation pop = parent.getObjectPopulation(structureIdx);
            if (pop.getRegions().isEmpty()) continue;
            //logger.debug("quality was : {}", Utils.toStringList(pop.getObjects(), o->o.getQuality()));
            for (StructureObject parentB : parent.getChildObjects(1)) {
                List<Region> objects = parentB.getRegion().getIncludedObjects(pop.getRegions());
                if (objects.isEmpty()) continue;
                MutationSegmenter currentSeg = (MutationSegmenter)ps.getSegmenter();
                if (!pthMapParametrizer.isEmpty()) pthMapParametrizer.get(parent.getTrackHead()).apply(parent, currentSeg);
                currentSeg.setQuality(objects, parentB.getBounds(), parent.getPreFilteredImage(structureIdx).cropWithOffset(parentB.getBounds()), parent.getMask());
            }
            // transfer quality to structureObject & store
            for (StructureObject o : parent.getChildren(structureIdx)) o.setAttribute("Quality", o.getRegion().getQuality());
            dbRef.getDao(positionName).store(parent.getChildren(structureIdx));
            //logger.debug("quality is now : {}", Utils.toStringList(pop.getObjects(), o->o.getQuality()));
        }
    }
    protected void runPosition(String positionName) {
        Map<StructureObject, List<StructureObject>> parentTrackRef = StructureObjectUtils.getAllTracks(dbRef.getDao(positionName).getRoots(), parentStructureIdx);
        Map<StructureObject, List<StructureObject>> parentTrack = StructureObjectUtils.getAllTracks(db.getDao(positionName).getRoots(), parentStructureIdx);
        if (parentTrackRef.size()!=parentTrack.size()) throw new RuntimeException("DB & Ref don't have same number of parents tracks @ position: "+positionName);
        List<StructureObject> thRef = new ArrayList<>(parentTrackRef.keySet());
        Collections.sort(thRef);
        List<StructureObject> th = new ArrayList<>(parentTrack.keySet());
        Collections.sort(th);
        int[] falsePositiveAndNegative = new int[3];
        List<StructureObject> fpObjects = this.fp!=null ? new ArrayList<>() : null;
        List<StructureObject> fnObjects = this.fn!=null ? new ArrayList<>() : null;
        List<StructureObject> fpRefObjects = this.fpRef!=null ? new ArrayList<>() : null;
        List<StructureObject> fnRefObjects = this.fnRef!=null ? new ArrayList<>() : null;
        for (int mcIdx = 0; mcIdx<parentTrackRef.size(); ++mcIdx) {
            List<StructureObject> refTrack = parentTrackRef.get(thRef.get(mcIdx));
            List<StructureObject> track = parentTrack.get(th.get(mcIdx));
            if (refTrack.size()!=track.size()) throw new RuntimeException("DB & Ref track don't have same length @ position: "+positionName);
            for (int frame = 0; frame<refTrack.size(); ++frame) compareObjects(refTrack.get(frame), track.get(frame), falsePositiveAndNegative, fpObjects, fnObjects,  fpRefObjects, fnRefObjects);
        }
        synchronized(countLock) {
            falsePositive+=falsePositiveAndNegative[0];
            falseNegative+=falsePositiveAndNegative[1];
            totalRef+=falsePositiveAndNegative[2];
            if (fnObjects!=null && !fnObjects.isEmpty()) {
                db.getDao(positionName).store(fnObjects);
                fn.addElements(fnObjects);
            }
            if (fpObjects!=null && !fpObjects.isEmpty()) fp.addElements(fpObjects);
            if (fnRefObjects!=null && !fnRefObjects.isEmpty()) fnRef.addElements(fnRefObjects);
            if (fpRefObjects!=null && !fpRefObjects.isEmpty()) {
                dbRef.getDao(positionName).store(fpRefObjects);
                fpRef.addElements(fpRefObjects);
            }
        }
    }
    protected void compareObjects(StructureObject pRef, StructureObject p, int[] fpn, List<StructureObject> fp, List<StructureObject> fn, List<StructureObject> fpRef, List<StructureObject> fnRef) {
        List<StructureObject> objects = new ArrayList<>(p.getChildren(structureIdx));
        int currentObjectNumber = objects.size();
        List<StructureObject> objectsRef = new ArrayList<>(pRef.getChildren(structureIdx));
        int refObjectNumber = objectsRef.size();
        fpn[2]+=objectsRef.size();
        //logger.debug("compare objects: {}={} & {}={}", pRef, objectsRef.size(), p, objects.size()); //if (!objects.isEmpty() || !objectsRef.isEmpty() ) 
        Iterator<StructureObject> it = objects.iterator();
        while(it.hasNext()) {
            StructureObject o = it.next();
            if (objectsRef.isEmpty()) { // false positive spot
                //logger.debug("no assignment for: {}", o);
                ++fpn[0];
                if (fp!=null) fp.add(o);
            } else { // simple assign by decreasing distCC
                HashMapGetCreate<StructureObject, Double> distMap = new HashMapGetCreate<>(oo->GeometricalMeasurements.getDistanceSquare(oo.getRegion(), o.getRegion()));
                StructureObject closest = Collections.min(objectsRef, Utils.comparator(distMap));
                double d = distMap.getAndCreateIfNecessary(closest); // case of n==1
                if (d<=distCCThldSq) { // assigned
                    objectsRef.remove(closest);
                    it.remove();
                    //logger.debug("{} assigned to {}, by distance: {}", o, closest, d);
                } 
                else  {
                    //logger.debug("no assignment for: {}", o);
                    ++fpn[0];
                    if (fp!=null) fp.add(o);
                }
            }
        }
        //logger.debug("pos:{}, parent:{}, remaining objects ref: {}, current: {}", pRef.getPositionName(), pRef, objectsRef.size(), objects.size());
        if (!objectsRef.isEmpty()) { // false negative
            if (unshureObjects!=null) objectsRef.removeIf(o->unshureObjects.contains(o));
            fpn[1]+=objectsRef.size();
            if (fn!=null) { // duplicate objects and add to corresponding parent
                List<StructureObject> fnToAdd = Utils.transform(objectsRef, o->o.duplicate());
                int count = currentObjectNumber;
                for (StructureObject o : fnToAdd) {
                    o.setIdx(count++);
                    o.setParent(p);
                }
                fn.addAll(fnToAdd);
            }
        }
        // selections in reference experiment
        if (!objectsRef.isEmpty() && fnRef!=null) {
            fnRef.addAll(objectsRef);
        }
        if (!objects.isEmpty() && fpRef!=null) { // false positive: // duplicate objects and add to corresponding parent in REF XP
            List<StructureObject> fpToAdd = Utils.transform(objects, o->o.duplicate());
            int count = refObjectNumber;
            for (StructureObject o : fpToAdd) {
                o.setIdx(count++);
                o.setParent(pRef);
            }
            fpRef.addAll(fpToAdd);
        }
    }
}
