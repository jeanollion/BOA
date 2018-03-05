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
import boa.data_structure.Voxel;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.image.processing.bacteria_skeleton.BacteriaSpine;
import boa.image.processing.bacteria_skeleton.BacteriaSpineCoord;
import boa.image.processing.bacteria_skeleton.BacteriaSpineLocalizer;
import boa.image.processing.bacteria_skeleton.CircularNode;
import boa.plugins.PluginFactory;
import static boa.test_utils.TestUtils.logger;
import boa.utils.geom.Point;
import boa.utils.geom.PointContainer2;
import boa.utils.geom.Vector;
import ij.ImageJ;

/**
 *
 * @author jollion
 */
public class TestSkeletonize {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        new ImageJ();
        //String dbName = "AyaWT_mmglu";
        String dbName = "MutH_150324";
        int fieldNumber= 0, timePoint=0, mc=0, b=0; // F=2 B=1
        MasterDAO mDAO = new Task(dbName).getDB();
        MicroscopyField f = mDAO.getExperiment().getPosition(fieldNumber);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(timePoint);
        StructureObject bact = root.getChildren(0).get(mc).getChildren(1).get(b);
        CircularNode<Voxel>[] contour = new CircularNode[1];
        PointContainer2<Vector, Double>[] spine = BacteriaSpine.createSpine(bact.getRegion(), contour);
        Image test = BacteriaSpine.drawSpine(bact.getBounds(), spine, contour[0]);
        // test localization
        double[] center = bact.getRegion().getGeomCenter(false);
        Point p = new Point((float)center[0], (float)center[1]);
        Point pT = p.duplicate().translateRev(bact.getBounds());
        test.setPixel((int)(pT.get(0)*5+0.5)+1, (int)(pT.get(1)*5+0.5)+1, 0, 1000);
        BacteriaSpineLocalizer loc = new BacteriaSpineLocalizer(bact.getRegion());
        BacteriaSpineCoord coord = loc.getCoord(p, BacteriaSpineLocalizer.ReferencePole.FirstPole, BacteriaSpineLocalizer.Compartment.WholeCell);
        Point p2 = loc.project(coord);
        Point p2T = p2.duplicate().translateRev(bact.getBounds());
        test.setPixel((int)(p2T.get(0)*5+0.5)+1, (int)(p2T.get(1)*5+0.5)+1, 0, 1001);
        coord.normalizedSpineCoordinate = coord.normalizedSpineCoordinate * spine[spine.length-1].getContent2();
        logger.debug("coords: {}", coord);
        logger.debug("testDistance: {}", p2.dist(p));
        ImageWindowManagerFactory.showImage(test);
    }

}
