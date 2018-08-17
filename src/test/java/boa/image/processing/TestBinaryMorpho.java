/*
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.image.processing;

import boa.configuration.experiment.Position;
import boa.core.Task;
import boa.data_structure.ProcessingTest;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.data_structure.dao.MasterDAO;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.SimpleBoundingBox;
import boa.image.TypeConverter;
import boa.image.processing.neighborhood.Neighborhood;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Jean Ollion
 */
public class TestBinaryMorpho {
    public static final Logger logger = LoggerFactory.getLogger(TestBinaryMorpho.class);
    public static void main(String[] args) {
        String dbName = "MutH_150324";
        MasterDAO mDAO = new Task(dbName).getDB();
        
        Position f = mDAO.getExperiment().getPosition(0);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(370);
        StructureObject mc = root.getChildren(0).get(0);
        StructureObject bact = mc.getChildren(1).get(0);
        ImageInteger in = bact.getRegion().getMaskAsImageInteger();
        //ImageWindowManagerFactory.showImage(in);
        for (double radius = 1; radius <=10; ++radius) {
            logger.debug("radius: {}", radius);
            compareEDTAndNormal(in, radius, true);
            compareEDTAndNormal(in, radius, false);
        }
        // make a dataset and 
        List<StructureObject> bacts = StructureObjectUtils.getAllChildren(mDAO.getDao(f.getName()).getRoots(), 1);
        int lim = 2000;
        if (bacts.size()>lim) bacts = bacts.subList(0, lim);
        for (double radius = 1; radius <=10; ++radius) {
            double rad = radius;
            long t0 = System.currentTimeMillis();
            bacts.parallelStream().forEach(b-> filter(b.getRegion().getMaskAsImageInteger(), rad, true, true, false));
            long t1 = System.currentTimeMillis();
            bacts.parallelStream().forEach(b-> filter(b.getRegion().getMaskAsImageInteger(), rad, false, true, false));
            long t2 = System.currentTimeMillis();
            bacts.parallelStream().forEach(b-> filter(b.getRegion().getMaskAsImageInteger(), rad, true, false, false));
            long t3 = System.currentTimeMillis();
            bacts.parallelStream().forEach(b-> filter(b.getRegion().getMaskAsImageInteger(), rad, false, false, false));
            long t4 = System.currentTimeMillis();
            logger.debug("Radius: {}, open edt: {} vs: {}, close edt: {} vs: {}", radius, t1-t0, t2-t1, t3-t2, t4-t3);
        }
        
        // for 2D bacteria -> EDT performs better if radius >=8 . TODO: see for 3D
    }
    
    private static void compareEDTAndNormal(ImageInteger in, double radius, boolean open) {
        ImageMask edt = filter(in, radius, true, open, true);
        ImageMask normal = filter(in, radius, false, open, true);
        if (!edt.sameDimensions(normal)) {
            logger.error("images don't have same dimensions: edt: {}, normal: {}, open: {}, radius: {}", new SimpleBoundingBox(edt), new SimpleBoundingBox(normal), open, radius);
            return;
        }
        if (!edt.sameBounds(normal)) {
            logger.error("images don't have same bounds: edt: {}, normal: {}, open: {}, radius: {}", new SimpleBoundingBox(edt), new SimpleBoundingBox(normal), open, radius);
            return;
        }
        // compare pix to pix
        boolean[] res = new boolean[1];
        BoundingBox.loop(new SimpleBoundingBox(in).resetOffset(), (x, y, z)-> {
            if (edt.insideMask(x, y, z)!=normal.insideMask(x, y, z)) res[0] =  true;
        }, false);
        if (res[0]) {
            logger.error("edt and normal filtering differ, open: {}, radius: {}", open, radius);
            ImageInteger edtI = TypeConverter.toImageInteger(edt, null);
            ImageInteger normalI = TypeConverter.toImageInteger(normal, null);
            ImageWindowManagerFactory.showImage(Image.mergeZPlanes(normalI, edtI).setName(open?"Open":"close"+" - rad: "+radius));
        }
    }
    private static ImageMask filter(ImageInteger in, double radius, boolean edt, boolean open, boolean parallele) {
        if (edt) {
            if (open) return BinaryMorphoEDT.binaryOpen(in, radius, 0, parallele);
            else return BinaryMorphoEDT.binaryClose(in, radius, 0, parallele);
        } else {
            Neighborhood n = Filters.getNeighborhood(radius, in);
            if (open) return Filters.binaryOpen(in, null, n, parallele);
            else  return Filters.binaryCloseExtend(in, n, parallele);
        }
    }
}
