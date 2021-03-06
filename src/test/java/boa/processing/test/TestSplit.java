/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
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
import boa.image.Image;
import java.util.List;
import boa.plugins.Measurement;
import boa.plugins.ObjectSplitter;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.LocalSNR;

/**
 *
 * @author Jean Ollion
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
        GUI.getInstance().openExperiment(dbName, null, true);
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
        splitter.splitObject(parent, structureIdx, o.getRegion());
        
    }
}
