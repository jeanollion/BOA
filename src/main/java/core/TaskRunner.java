/*
 * Copyright (C) 2016 jollion
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

import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import measurement.MeasurementKeyObject;
import measurement.extraction.DataExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import plugins.PluginFactory;
import utils.ArrayUtil;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class TaskRunner {
    public static final Logger logger = LoggerFactory.getLogger(TaskRunner.class);
    
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        
        //List<Task> tasks = extractMeasurementOnFluoXP(true);
        List<Task> tasks = getTasks();
        //for (Task t : tasks) t.isValid();
        for (Task t : tasks) if (t.isValid()) t.run();
        logger.info("All tasks performed! See errors below:");
        for (Task t : tasks) t.printErrors();
    }
    public static List<Task> getTasks() {
        List<Task> tasks = new ArrayList<Task>() {{
            //add(new Task("boa_fluo160428").setPositions(1, 2, 23, 3, 4, 5, 6).addExtractMeasurementDir("/home/jollion/Documents/LJP/Analyse/MutationTracks", 1).addExtractMeasurementDir("/home/jollion/Documents/LJP/Analyse/MutationTracks", 2));
            add(new Task("boa_phase150616wt").setActions(false, true, true, true).setStructures(1).addExtractMeasurementDir("/data/Images/Phase/150616_6300_wt/", 1).addExtractMeasurementDir("/data/Images/Phase/150616_6300_wt/", 0));
            add(new Task("boa_phase141107wt").setActions(false, true, true, true).setStructures(1).addExtractMeasurementDir("/data/Images/Phase/141107_mg6300_wt/", 1).addExtractMeasurementDir("/data/Images/Phase/141107_mg6300_wt/", 0));
            add(new Task("boa_phase150324mutH").setActions(false, true, true, true).setStructures(1).addExtractMeasurementDir("/data/Images/Phase/150324_6300_mutH/", 1).addExtractMeasurementDir("/data/Images/Phase/150324_6300_mutH/", 0));
        }};
        return tasks;
    }
    
    public static List<Task> extractMeasurementOnFluoXP(boolean runMeas) {
        List<Task> tasks = new ArrayList<Task>() {{
            add(new Task("boa_fluo151127").setActions(false, false, false, runMeas).setPositions(1, 2, 3, 4).addExtractMeasurementDir("/home/jollion/Documents/LJP/Analyse/MutationTracks", 1).addExtractMeasurementDir("/home/jollion/Documents/LJP/Analyse/MutationTracks", 2));
            add(new Task("boa_fluo160428").setActions(false, false, false, runMeas).setPositions(1, 2, 23, 3, 4, 5, 6).addExtractMeasurementDir("/home/jollion/Documents/LJP/Analyse/MutationTracks", 1).addExtractMeasurementDir("/home/jollion/Documents/LJP/Analyse/MutationTracks", 2));
            add(new Task("boa_fluo160501").setActions(false, false, false, runMeas).setPositions(1, 2, 4).addExtractMeasurementDir("/home/jollion/Documents/LJP/Analyse/MutationTracks", 1).addExtractMeasurementDir("/home/jollion/Documents/LJP/Analyse/MutationTracks", 2));
        }};
        return tasks;
    }
    
    public static class Task {
        final String dbName;
        boolean preProcess, segmentAndTrack, trackOnly, measurements;
        int[] positions;
        int[] structures;
        List<Pair<String, int[]>> extrackMeasurementDir = new ArrayList<>();
        List<Pair<String, Exception>> errors = new ArrayList<>();
        MasterDAO db;
        
        public Task(String dbName) {
            this.dbName=dbName;
        }
        public Task setAllActions() {
            this.preProcess=true;
            this.segmentAndTrack=true;
            this.measurements=true;
            this.trackOnly=false;
            return this;
        }
        public Task setActions(boolean preProcess, boolean segment, boolean track, boolean measurements) {
            this.preProcess=preProcess;
            this.segmentAndTrack=segment;
            if (segmentAndTrack) trackOnly = false;
            else trackOnly = track;
            this.measurements=measurements;
            return this;
        }
        public Task setPositions(int... positions) {
            if (positions!=null && positions.length>0) this.positions=positions;
            return this;
        }
        
        public Task setStructures(int... structures) {
            if (structures!=null && structures.length>0) this.structures=structures;
            return this;
        }
        
        public Task addExtractMeasurementDir(String dir, int... extractStructures) {
            if (extractStructures!=null && extractStructures.length==0) extractStructures = null;
            this.extrackMeasurementDir.add(new Pair(dir, extractStructures));
            return this;
        }
        public boolean isValid() {
            db = new MorphiumMasterDAO(dbName);
            if (db.getExperiment()==null) {
                errors.add(new Pair(dbName, new Exception("DB: "+ dbName+ " not found")));
                printErrors();
                db = null;
                return false;
            } else {
                if (structures!=null) checkArray(structures, db.getExperiment().getStructureCount(), "Invalid structure: ");
                if (positions!=null) checkArray(positions, db.getExperiment().getPositionCount(), "Invalid position: ");
                // check files
                for (Pair<String, int[]> e : extrackMeasurementDir) {
                    File f= new File(e.key);
                    if (!f.exists()) errors.add(new Pair(dbName, new Exception("File: "+ e.key+ " not found")));
                    else if (!f.isDirectory()) errors.add(new Pair(dbName, new Exception("File: "+ e.key+ " is not a directory")));
                    else if (e.value!=null) checkArray(e.value, db.getExperiment().getStructureCount(), "Extract structure for dir: "+e.value+": Invalid structure: ");
                }
            }
            if (!measurements && !preProcess && !segmentAndTrack && ! trackOnly && extrackMeasurementDir.isEmpty()) errors.add(new Pair(dbName, new Exception("No action to run!")));
            db=null;
            printErrors();
            logger.info("task : {}, isValid: {}", dbName, errors.isEmpty());
            return errors.isEmpty();
        }
        private void checkArray(int[] array, int maxValue, String message) {
            if (array[ArrayUtil.max(array)]>=maxValue) errors.add(new Pair(dbName, new Exception(message + array[ArrayUtil.max(array)]+ " not found")));
            if (array[ArrayUtil.min(array)]<0) errors.add(new Pair(dbName, new Exception(message + array[ArrayUtil.min(array)]+ " not found")));
        }
        public void printErrors() {
            for (Pair<String, Exception> e : errors) {
                logger.error(e.key, e.value);
            }
        }
        public void run() {
            db = new MorphiumMasterDAO(dbName);
            if (positions==null) positions=ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount());
            if (structures==null) structures = ArrayUtil.generateIntegerArray(db.getExperiment().getStructureCount());
            
            boolean needToDeleteObjects = preProcess || segmentAndTrack;
            boolean deleteAll =  needToDeleteObjects && structures.length==db.getExperiment().getStructureCount() && positions.length==db.getExperiment().getPositionCount();
            if (deleteAll) db.deleteAllObjects();
            boolean deleteAllField = needToDeleteObjects && structures.length==db.getExperiment().getStructureCount() && !deleteAll;
            logger.info("Run task: db: {} preProcess: {}, segmentAndTrack: {}, trackOnly: {}, runMeasurements: {}, need to delete objects: {}, delete all: {}, delete all by field: {}", dbName, preProcess, segmentAndTrack, trackOnly, measurements, needToDeleteObjects, deleteAll, deleteAllField);
            
            for (int pIdx : positions) {
                String position = db.getExperiment().getPosition(pIdx).getName();
                try {
                    run(position, deleteAllField);
                } catch (Exception e) {
                    errors.add(new Pair(position, e));
                }
            }
            
            for (Pair<String, int[]> e  : this.extrackMeasurementDir) extract(e.key, e.value);
            
            db=null;
        }
        private void run(String position, boolean deleteAllField) {
            
            if (deleteAllField) db.getDao(position).deleteAllObjects();
            
            if (preProcess) {
                logger.info("Pre-Processing: Position: {}", position);
                Processor.preProcessImages(db.getExperiment().getPosition(position), db.getDao(position), true, preProcess);
            }
            if (segmentAndTrack || trackOnly) {
                logger.info("Processing: Position: {}", position);
                List<Pair<String, Exception>> e = Processor.processAndTrackStructures(db.getDao(position), true, trackOnly, structures);
                errors.addAll(e);
            }
            if (measurements) {
                logger.info("Measurements: Field: {}", position);
                db.getDao(position).deleteAllMeasurements();
                List<Pair<String, Exception>> e = Processor.performMeasurements(db.getDao(position));
                errors.addAll(e);
            }
            if (preProcess) db.updateExperiment(); // save field preProcessing configuration value @ each field
            db.getDao(position).clearCache();
            db.getExperiment().getPosition(position).flushImages();
        }
        private void extract(String dir, int[] structures) {
            if (structures==null) structures = ArrayUtil.generateIntegerArray(db.getExperiment().getStructureCount());
            String file = dir+File.separator+db.getDBName()+Utils.toStringArray(structures, "_", "", "_")+".xls";
            logger.info("measurements will be extracted to: {}", file);
            Map<Integer, String[]> keys = db.getExperiment().getAllMeasurementNamesByStructureIdx(MeasurementKeyObject.class, structures);
            DataExtractor.extractMeasurementObjects(db, file, getPositionNames(), keys);
        }
        private List<String> getPositionNames() {
            if (positions==null) positions=ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount());
            List<String> res = new ArrayList<>(positions.length);
            for (int i = 0; i<positions.length; ++i) res.add(db.getExperiment().getPosition(positions[i]).getName());
            return res;
        }
    }
}
