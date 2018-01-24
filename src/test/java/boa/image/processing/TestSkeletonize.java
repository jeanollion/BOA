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
package boa.image.processing;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.core.Task;
import boa.configuration.experiment.MicroscopyField;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.StructureObject;
import ij.ImagePlus;
import boa.image.IJImageWrapper;
import boa.image.Image;
import boa.image.TypeConverter;
import sc.fiji.skeletonize3D.Skeletonize3D_;

/**
 *
 * @author jollion
 */
public class TestSkeletonize {
    public static void main(String[] args) {
        String dbName = "boa_fluo160501";
        int fieldNumber= 0, timePoint=0, mc=0, b=1;
        MasterDAO mDAO = new Task(dbName).getDB();
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(timePoint);
        StructureObject bact = root.getChildren(0).get(mc).getChildren(1).get(b);
        Image sk = TypeConverter.toByteMask(bact.getMask(), null, 255);
        IJImageDisplayer disp  = new IJImageDisplayer();
        disp.showImage(sk.duplicate("input"));
        ImagePlus skeleton = IJImageWrapper.getImagePlus(sk);
        final Skeletonize3D_ skeletoniser = new Skeletonize3D_();
        skeletoniser.setup("", skeleton);
        skeletoniser.run(null);
        disp.showImage(sk);
    }

}
