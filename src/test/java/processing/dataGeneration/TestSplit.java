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
import boa.gui.selection.SelectionUtils;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import ij.ImageJ;
import image.Image;
import java.util.List;
import plugins.Measurement;
import plugins.ObjectSplitter;
import plugins.PluginFactory;
import plugins.plugins.measurements.ObjectFeatures;
import plugins.plugins.measurements.SimpleIntensityMeasurement;
import plugins.plugins.measurements.SimpleIntensityMeasurementStructureExclusion;
import plugins.plugins.measurements.objectFeatures.LocalSNR;

/**
 *
 * @author jollion
 */
public class TestSplit {
    public static void main(String[] args) {
        new ImageJ();
        String dbName = "boa_phase141107wt";
        int fIdx = 0;
        int frame =390;
        int mcIdx = 0;
        int objectIdx = 1;
        int structureIdx = 1;
        //MasterDAO db = new MorphiumMasterDAO(dbName);
        GUI.getInstance().setDBConnection(dbName, null, true);
        MasterDAO db = GUI.getDBConnection();
        LocalSNR.debug=true;
        testSplitOnSingleObject(null, db, fIdx, frame, structureIdx, mcIdx, objectIdx);
    }
    public static void testSplitOnSingleObject(ObjectSplitter splitter, MasterDAO db, int fIdx, int frame, int structureIdx, int mcIdx, int oIdx) {
        if (splitter==null) splitter = db.getExperiment().getStructure(structureIdx).getObjectSplitter();
        StructureObject parent = db.getDao(db.getExperiment().getPosition(fIdx).getName()).getRoot(frame);
        StructureObject o;
        if (structureIdx ==0 ) o = parent.getChildren(0).get(oIdx);
        else {
            parent = parent.getChildren(0).get(mcIdx);
            o = parent.getChildren(structureIdx).get(oIdx);
        }
        
        splitter.setSplitVerboseMode(true);
        Image input = parent.getRawImage(structureIdx);
        input = input.cropWithOffset(o.getBounds());
        splitter.splitObject(input, o.getObject());
        
    }
}
