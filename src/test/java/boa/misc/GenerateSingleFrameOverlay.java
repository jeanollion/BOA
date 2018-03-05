/*
 * Copyright (C) 2018 jollion
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
package boa.misc;

import boa.core.Processor;
import boa.core.Task;
import boa.data_structure.StructureObject;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.input_image.InputImages;
import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.plugins.PluginFactory;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import boa.utils.Utils;
import ij.ImageJ;
import ij.ImagePlus;

/**
 *
 * @author jollion
 */
public class GenerateSingleFrameOverlay {
    
    
    
    public static void main(String[] args) {
        new ImageJ();
        PluginFactory.findPlugins("plugins.plugins");
        String dbName = "fluo160501";
        int positionIdx = 16;
        int frame = 3;
        MasterDAO db = new Task(dbName).getDB();
        StructureObject root = Utils.getFirst(Processor.getOrCreateRootTrack(db.getDao(db.getExperiment().getPosition(positionIdx).getName())), r->r.getFrame()==frame);
        Image bact = root.getRawImage(1);
        Image mut = root.getRawImage(2);
        IJSubtractBackground.filter(mut, 50, false, true, false, false);
        IJSubtractBackground.filter(bact, 50, false, true, false, false);
        ImagePlus merge = ij.plugin.RGBStackMerge.mergeChannels(new ImagePlus[]{IJImageWrapper.getImagePlus(bact), IJImageWrapper.getImagePlus(mut)}, true);
        //merge.setOverlay(((ImagePlus)iwm.getDisplayer().getImage(imMut)).getOverlay());
        merge.show();
    }
}
