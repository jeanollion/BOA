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

import dataStructure.objects.MorphiumMasterDAO;
import static core.Processor.logger;
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.Measurements;
import dataStructure.objects.MeasurementsDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.Selection;
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
import java.util.function.Function;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class DataExtractor {
    
    final static String separator =";";
    final static String NaN = "NaN";
    int structureIdx;
    MasterDAO db;
    public static Function<Number, String> numberFormater = (Number n) -> { // precision
        if (n instanceof Integer) {
            return n.toString();
        } else {
            double d = Math.abs(n.doubleValue());
            if (d==0) return "0";
            else if (d < 1 || d > 100) {
                return String.format(java.util.Locale.US, "%.4E", n);
            } else {
                return String.format(java.util.Locale.US, "%.4f", n);
            }
        }
    };
    public DataExtractor(MasterDAO db, int structureIdx) {
        this.db=db;
        this.structureIdx=structureIdx;
    }
    protected String getBaseHeader() { //TODO split Indicies column ...
        int[] path = db.getExperiment().getPathToRoot(structureIdx);
        String[] structureNames = db.getExperiment().getStructureNames(path);
        String res =  "Position"+separator+"PositionIdx"+separator+"Indices"+separator+"Frame";
        for (String s : structureNames) res+=separator+s+"Idx";
        return res;
    }
    protected String getBaseLine(Measurements m) {
        String line = m.getFieldName()+separator+db.getExperiment().getPositionIdx(m.getFieldName());
        int[] idx = m.getIndices();
        line+= Utils.toStringArray(idx, separator, "", Selection.indexSeparator);
        for (int i : idx) line+=separator+i; // separated columns ..
        return line;
    }
    
    protected String getHeader(ArrayList<String> measurements) {
        String header = getBaseHeader();
        for (String m : measurements) header+=separator+m;
        //logger.debug("extract data: header: {}", header);
        return header;
    }
    protected static ArrayList<String> getAllMeasurements(Map<Integer, String[]> measurements) {
        ArrayList<String> l = new ArrayList<String>();
        for (String[] s : measurements.values()) l.addAll(Arrays.asList(s));
        return l;
    }
    public static void extractMeasurementObjects(MorphiumMasterDAO db, String outputFile, int structureIdx,  List<String> positions, String... measurements) {
        Map<Integer, String[]> map = new HashMap<Integer, String[]>(1);
        map.put(structureIdx, measurements);
        DataExtractor de= new DataExtractor(db, structureIdx);
        de.extractMeasurementObjects(outputFile, positions, map);
    }
    public static void extractMeasurementObjects(MasterDAO db, String outputFile, List<String> positions, Map<Integer, String[]> allMeasurements) {
        TreeMap<Integer, String[]> allMeasurementsSort = new TreeMap<Integer, String[]>(allMeasurements);
        if (allMeasurementsSort.isEmpty()) return;
        DataExtractor de= new DataExtractor(db, allMeasurementsSort.lastKey());
        de.extractMeasurementObjects(outputFile, positions, allMeasurementsSort);
    }
    protected void extractMeasurementObjects(String outputFile, List<String> positions, Map<Integer, String[]> allMeasurements) {
        Experiment xp = db.getExperiment();
        if (positions==null) positions = Arrays.asList(db.getExperiment().getPositionsAsString());
        long t0 = System.currentTimeMillis();
        FileWriter fstream;
        BufferedWriter out;
        int count = 0;
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
            String[] currentMeasurementNames = allMeasurementsSort.pollLastEntry().getValue();
            for (String fieldName : positions) {
                ObjectDAO dao = db.getDao(fieldName);
                TreeMap<Integer, List<Measurements>> parentMeasurements = new TreeMap<Integer, List<Measurements>>();
                for (Entry<Integer, String[]> e : allMeasurementsSort.entrySet()) parentMeasurements.put(e.getKey(), dao.getMeasurements(e.getKey(), e.getValue()));
                List<Measurements> currentMeasurements = dao.getMeasurements(currentStructureIdx, currentMeasurementNames);
                for (Measurements m : currentMeasurements) {
                    String line = getBaseLine(m);
                    // add measurements from parents of the the current structure
                    for (Entry<Integer, List<Measurements>> e : parentMeasurements.entrySet()) {
                        Measurements key = m.getParentMeasurementKey(parentOrder[e.getKey()]);
                        int pIdx = e.getValue().indexOf(key);
                        if (pIdx==-1) for (String pMeasName : allMeasurementsSort.get(e.getKey())) line+=separator+NaN; // parent not found, adds only NaN
                        else {
                            key = e.getValue().get(pIdx);
                            for (String pMeasName : allMeasurementsSort.get(e.getKey())) line+=separator+key.getValueAsString(pMeasName, numberFormater);
                        }
                    }
                    //add measurements from the current structure
                    for (String mName : currentMeasurementNames) line+=separator+m.getValueAsString(mName, numberFormater);
                    out.newLine();
                    out.write(line);
                    ++count;
                }
            }
            out.close();
            long t1 = System.currentTimeMillis();
            logger.debug("data extractions: {} line in: {} ms", count, t1-t0);
        } catch (IOException ex) {
            logger.debug("init extract data error: {}", ex);
        }
    }
    
}
