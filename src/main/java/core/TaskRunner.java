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
import dataStructure.objects.MorphiumObjectDAO;
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
import org.apache.commons.lang.ArrayUtils;
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
        //List<Task> tasks = getFluoTasks();
        //for (Task t : tasks) t.isValid();
        for (Task t : tasks) if (t.isValid()) t.run();
        logger.info("All tasks performed! See errors below:");
        for (Task t : tasks) t.printErrors();
    }
    public static List<Task> getFluoTasks() {
        List<Task> tasks = new ArrayList<Task>() {{
            add(new Task("boa_fluo151127_test").setActions(false, true, true, false).setPositions(0).setStructures(1));
        }};
        return tasks;
    }
    
    public static List<Task> getTasks() {
        List<Task> tasks = new ArrayList<Task>() {{
            add(new Task("boa_phase150324mutH").setActions(false, true, true, true).addExtractMeasurementDir("/data/Images/Phase/150324_6300_mutH/", 1).addExtractMeasurementDir("/data/Images/Phase/150324_6300_mutH/", 0));
            add(new Task("boa_phase141107wt").setActions(false, true, true, true).addExtractMeasurementDir("/data/Images/Phase/141107_mg6300_wt/", 1).addExtractMeasurementDir("/data/Images/Phase/141107_mg6300_wt/", 0));
            add(new Task("boa_phase150616wt").setActions(false, true, true, true).addExtractMeasurementDir("/data/Images/Phase/150616_6300_wt/", 1).addExtractMeasurementDir("/data/Images/Phase/150616_6300_wt/", 0));
            //add(new Task("boa_phase150324mutH").setActions(false, true, true, true).setStructures(1).addExtractMeasurementDir("/data/Images/Phase/150324_6300_mutH/", 1).addExtractMeasurementDir("/data/Images/Phase/150324_6300_mutH/", 0));
            //add(new Task("boa_phase141107wt").setActions(false, true, true, true).setStructures(1).addExtractMeasurementDir("/data/Images/Phase/141107_mg6300_wt/", 1).addExtractMeasurementDir("/data/Images/Phase/141107_mg6300_wt/", 0));
            //add(new Task("boa_phase150616wt").setActions(false, true, true, true).setStructures(1).addExtractMeasurementDir("/data/Images/Phase/150616_6300_wt/", 1).addExtractMeasurementDir("/data/Images/Phase/150616_6300_wt/", 0));
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
    
    
}
