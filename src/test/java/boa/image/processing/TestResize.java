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
import boa.data_structure.StructureObject;
import boa.data_structure.dao.MasterDAO;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.image.Image;
import boa.plugins.plugins.pre_filters.Sigma;
import ij.ImageJ;

/**
 *
 * @author Jean Ollion
 */
public class TestResize {
    public static void main(String[] args) {
        new ImageJ();
        String dbName = "fluo171113_WT_15s";
        int postition= 0, frame=27, mcIdx=2;// F=2 B=1
        MasterDAO mDAO = new Task(dbName).getDB();
        Position f = mDAO.getExperiment().getPosition(postition);
        StructureObject root = mDAO.getDao(f.getName()).getRoots().get(frame);
        StructureObject mc = root.getChildren(0).get(mcIdx);
        Image image = mc.getRawImage(2);
        ImageWindowManagerFactory.showImage(image);
        testSigma(image, 1);
        testSigma(image, 2);
    }
    private static void testSigma(Image image, double factor) {
        Image resized  = ImageOperations.resizeXY(image,  (int) (image.sizeX()*factor), (int) (image.sizeY()*factor), ImageOperations.IJInterpolation.BICUBIC);
        //ImageWindowManagerFactory.showImage(resized.setName("resized "+factor));
        
        Image filt = Sigma.filter(resized, 3 * factor, 1, 0 * factor, 1, true);
        //Image filtOriginal = ImageOperations.resizeXY(filt, image.sizeX(), image.sizeY(), ImageOperations.IJInterpolation.BICUBIC);
        //ImageWindowManagerFactory.showImage(filt.setName(" filt resized "+factor));
        ImageWindowManagerFactory.showImage(filt.setName(" filt "+factor));
    }

    }