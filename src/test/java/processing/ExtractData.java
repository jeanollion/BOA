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
package processing;

import static TestUtils.Utils.logger;
import boa.gui.objects.DBConfiguration;
import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.StructureObject;
import image.Image;
import image.ImageMask;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import measurement.BasicMeasurements;

/**
 *
 * @author jollion
 */
public class ExtractData {
    DBConfiguration db;
    FileWriter fstream;
    BufferedWriter out;
    final static char separator =';';
    
    public static void main(String[] args) throws IOException {
        String dbName = "fluo1510";
        String fileDir  = "/data/Images/Fluo/films1510/outputData.xls";
        ExtractData e = new ExtractData();
        e.init(dbName, fileDir);
        e.extractFluoAndSpotNumber();
        logger.info("extract done!");
    }
    
    public void init(String dbName, String outputFile) {
        try {
            //String dbName = "testFluo6";
            
            db = new DBConfiguration(dbName);
            logger.info("Experiment: {} retrieved from db: {}", db.getExperiment().getName(), dbName);
            
            
            File output = new File(outputFile);
            output.delete();
            fstream = new FileWriter(output);
            out = new BufferedWriter(fstream);
            
            String headers = "FieldName"+separator+"MicrochannelIdx"+separator+"TimePoint"+separator+"MeanFluoIntensityBacteria"+separator+"MeanFluoIntensityMutation"+separator+"MeanFluoIntensityMutationForeground"+separator+"MutationNumber"+separator+"BacteriaNumber";
            out.write(headers);
            
            
        } catch (IOException ex) {
            logger.debug("init extract data error: {}", ex);
        }
        
    }
    public void extractFluoAndSpotNumber() throws IOException {
        for (int field = 0; field<db.getExperiment().getMicrocopyFieldCount(); ++field) {
            extractFluoAndSpotNumber(field);
        }
        out.close();
    }
    private void extractFluoAndSpotNumber(int field) throws IOException {
        MicroscopyField f = db.getExperiment().getMicroscopyField(field);
        
        final String startLine = f.getName()+separator;
        StructureObject root = db.getDao().getRoot(f.getName(), 0);
        ArrayList<StructureObject> mc0 = root.getChildObjects(0, db.getDao(), false);
        for (int mcIdx = 0; mcIdx<mc0.size(); ++mcIdx) {
            logger.info("exctract data: field: {}, micro channel: {}", field, mcIdx);
            ArrayList<StructureObject> mcTrack = db.getDao().getTrack(mc0.get(mcIdx));
            for (int t = 0; t<mcTrack.size(); ++t) {
                StructureObject s = mcTrack.get(t);
                double fluo = getFluo(s, 1, 1);
                if ( ! Double.isNaN(fluo)) { 
                    out.newLine();
                    String line = startLine+mcIdx+separator + s.getTimePoint() + separator + fluo + separator + getFluo(s, 2, 1)+separator + getFluo(s, 2, 2) + separator + getChildCount(s, 2)  + separator + getChildCount(s, 1);
                    out.write(line);
                } else break; // pas de bactérie -> on s'arrete ici
            }
        }
    }
    
    private double getFluo(StructureObject o, int imageStructureIdx, int objectStructureIdx) {
        Image im = o.getRawImage(imageStructureIdx);
        ArrayList<StructureObject> children = o.getChildObjects(objectStructureIdx, db.getDao(), false);
        double fluo = 0;
        double count = 0;
        for (StructureObject c : children) {
            fluo += BasicMeasurements.getMeanValue(c.getObject(), im) * c.getObject().getVoxels().size();
            count+=c.getObject().getVoxels().size();
        }
        return fluo/count;
    }
    
    private int getChildCount(StructureObject mc, int structureIdx) {
        return mc.getChildObjects(structureIdx, db.getDao(), false).size();
    }
}
