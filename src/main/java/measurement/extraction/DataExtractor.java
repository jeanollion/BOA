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
package measurement.extraction;

import boa.gui.objects.DBConfiguration;
import static core.Processor.logger;
import dataStructure.configuration.Experiment;
import dataStructure.objects.Measurements;
import dataStructure.objects.MeasurementsDAO;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import static measurement.extraction.ExtractData.separator;

/**
 *
 * @author jollion
 */
public class DataExtractor {
    
    final static char separator =';';
    final static char indexSeparator ='-';
    final static String NaN = "NaN";
    Experiment xp;
    MeasurementsDAO dao;
    public DataExtractor(Experiment xp, MeasurementsDAO dao) {
        this.dao=dao;
        this.xp=xp;
    }
    protected static String getBaseHeader() {
        return "FieldName"+separator+"Indicies"+separator+"TimePoint";
    }
    protected static String getBaseLine(Measurements m) {
        String line = m.getFieldName();
        int[] idx = m.getIndicies();
        if (idx.length==0) line+=separator+-1;
        else {
            line+=separator+idx[0];
            for (int i=1; i<idx.length; ++i) line+=indexSeparator+idx[i];
        }
        return line+separator+m.getTimePoint();
    }
    protected static String getHeader(ArrayList<String> measurements) {
        String header = getBaseHeader();
        for (String m : measurements) header+=separator+m;
        return header;
    }
    protected static ArrayList<String> getAllMeasurements(Map<Integer, String[]> measurements) {
        ArrayList<String> l = new ArrayList<String>();
        for (String[] s : measurements.values()) l.addAll(Arrays.asList(s));
        return l;
    }
    public void extractMeasurementObjects(String outputFile, int structureIdx, String... measurements) {
        HashMap<Integer, String[]> map = new HashMap<Integer, String[]>(1);
        map.put(structureIdx, measurements);
        extractMeasurementObjects(outputFile, map);
    }
    
    public void extractMeasurementObjects(String outputFile, Map<Integer, String[]> allMeasurements) {
        FileWriter fstream;
        BufferedWriter out;
        try {
            File output = new File(outputFile);
            output.delete();
            fstream = new FileWriter(output);
            out = new BufferedWriter(fstream);
            TreeMap<Integer, String[]> allMeasurementsSort = new TreeMap<Integer, String[]>(allMeasurements); // sort by structureIndex value
            out.write(getHeader(getAllMeasurements(allMeasurements))); 
            
            int currentStructureIdx = allMeasurementsSort.lastKey();
            int[] parentOrder = new int[currentStructureIdx]; // maps structureIdx to parent order
            for (int s : allMeasurementsSort.keySet()) {
                if (s!=currentStructureIdx) {
                    parentOrder[s] = xp.getPathToStructure(s, currentStructureIdx).length;
                }
            }
            for (String fieldName : xp.getFieldsAsString()) {
                String[] currentMeasurementNames = allMeasurementsSort.pollLastEntry().getValue();
                TreeMap<Integer, List<Measurements>> parentMeasurements = new TreeMap<Integer, List<Measurements>>();
                for (Entry<Integer, String[]> e : allMeasurementsSort.entrySet()) parentMeasurements.put(e.getKey(), dao.getMeasurements(fieldName, e.getKey(), e.getValue()));
                List<Measurements> currentMeasurements = dao.getMeasurements(fieldName, currentStructureIdx, currentMeasurementNames);
                for (Measurements m : currentMeasurements) {
                    String line = getBaseLine(m);
                    // add measurements from parents of the the current structure
                    for (Entry<Integer, List<Measurements>> e : parentMeasurements.entrySet()) {
                        Measurements key = m.getParentMeasurementKey(parentOrder[e.getKey()]);
                        int pIdx = e.getValue().indexOf(key);
                        if (pIdx==-1) for (String pMeasName : allMeasurementsSort.get(e.getKey())) line+=separator+NaN; // parent not found, adds only NaN
                        else {
                            key = e.getValue().get(pIdx);
                            for (String pMeasName : allMeasurementsSort.get(e.getKey())) line+=separator+key.getValueAsString(pMeasName);
                        }
                    }
                    //add measurements from the current structure
                    for (String mName : currentMeasurementNames) line+=separator+m.getValueAsString(mName);
                    out.newLine();
                    out.write(line);
                }
            }
            out.close();
        } catch (IOException ex) {
            logger.debug("init extract data error: {}", ex);
        }
    }
    
}
