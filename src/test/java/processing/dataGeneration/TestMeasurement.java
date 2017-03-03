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
package processing.dataGeneration;

import boa.gui.GUI;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.StructureObject;
import ij.ImageJ;
import plugins.Measurement;
import plugins.PluginFactory;
import plugins.plugins.measurements.ObjectFeatures;
import plugins.plugins.measurements.SimpleIntensityMeasurement;
import plugins.plugins.measurements.SimpleIntensityMeasurementStructureExclusion;
import plugins.plugins.measurements.objectFeatures.LocalSNR;

/**
 *
 * @author jollion
 */
public class TestMeasurement {
    public static void main(String[] args) {
        new ImageJ();
        String dbName = "boa_fluo170207_150ms";
        int fIdx = 103;
        int frame =0;
        int dispStructure = 2;
        //MasterDAO db = new MorphiumMasterDAO(dbName);
        GUI.getInstance().setDBConnection(dbName, null);
        MasterDAO db = GUI.getDBConnection();
        //Measurement m  = new SimpleIntensityMeasurementStructureExclusion(1, 2, 2).setRadii(4, 0).setPrefix("YFPExcl");
        //Measurement m  = new SimpleIntensityMeasurement(1, 2);
        Measurement m = new ObjectFeatures(2).addFeature(new LocalSNR(1), "Local SNR");
        LocalSNR.debug=true;
        testMeasurementOnSingleObject(m, db, fIdx, frame, 0, dispStructure);
    }
    public static void testMeasurementOnSingleObject(Measurement m, MasterDAO db, int fIdx, int frame, int cIdx, int dispStructure) {
        db.getExperiment().addMeasurement(m); // in case experiment is needed
        int s = m.getCallStructure();
        
        StructureObject root = db.getDao(db.getExperiment().getPosition(fIdx).getName()).getRoot(frame);
        StructureObject o = root.getChildren(s).get(cIdx);
        
        m.performMeasurement(o);
        
        ImageObjectInterface i = ImageWindowManagerFactory.getImageManager().getImageObjectInterface(o.getParent(), s, true);
        ImageWindowManagerFactory.getImageManager().addImage(i.generateRawImage(dispStructure), i, dispStructure, false, true);
        ImageWindowManagerFactory.getImageManager().setInteractiveStructure(2);
        
    }
}
