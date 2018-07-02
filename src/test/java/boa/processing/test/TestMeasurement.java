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
package boa.processing.test;

import boa.ui.GUI;
import boa.gui.image_interaction.InteractiveImage;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.data_structure.SelectionUtils;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import ij.ImageJ;
import java.util.List;
import boa.plugins.Measurement;
import boa.plugins.PluginFactory;
import boa.plugins.plugins.measurements.ObjectFeatures;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.LocalSNR;

/**
 *
 * @author jollion
 */
public class TestMeasurement {
    public static void main(String[] args) {
        new ImageJ();
        String dbName = "boa_fluo170207_150ms";
        int fIdx = 0;
        int frame =0;
        int parentdispStructure = 0;
        int dispStructure = 2;
        //MasterDAO db = new MorphiumMasterDAO(dbName);
        GUI.getInstance().openExperiment(dbName, null, true);
        MasterDAO db = GUI.getDBConnection();
        //Measurement m  = new SimpleIntensityMeasurementStructureExclusion(1, 2, 2).setRadii(4, 0).setPrefix("YFPExcl");
        //Measurement m  = new SimpleIntensityMeasurement(1, 2);
        Measurement m = new ObjectFeatures(2).addFeature(new LocalSNR(1).setFormula(1, 2), "LocalContrast");
        LocalSNR.debug=true;
        testMeasurementOnSingleObject(m, db, fIdx, frame, 0, 10, parentdispStructure, dispStructure);
    }
    public static void testMeasurementOnSingleObject(Measurement m, MasterDAO db, int fIdx, int frame, int cIdx, int cIdxMax, int parentdispStructure, int dispStructure) {
        db.getExperiment().addMeasurement(m); // in case experiment is needed
        int s = m.getCallStructure();
        
        StructureObject root = db.getDao(db.getExperiment().getPosition(fIdx).getName()).getRoot(frame);
        List<StructureObject> ob = root.getChildren(s); 
        ob = ob.subList(cIdx, Math.min(cIdxMax, ob.size()));
        for (StructureObject o : ob) m.performMeasurement(o);
        SelectionUtils.displaySelection(new Selection("testMeas", db).addElements(ob), parentdispStructure, dispStructure);
        ImageWindowManagerFactory.getImageManager().setInteractiveStructure(2);
        
    }
}
