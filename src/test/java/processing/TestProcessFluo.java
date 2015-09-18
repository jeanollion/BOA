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
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.Structure;
import image.Image;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import plugins.plugins.transformations.AutoRotationXY;
import processing.ImageTransformation.InterpolationScheme;

/**
 *
 * @author jollion
 */
public class TestProcessFluo {
    Experiment xp;
    
    public static void main(String[] args) {
        new TestProcessFluo().testRotation();
    }
    @Test
    public void testImport() {
        xp = new Experiment("testXP", new Structure("structure"));
        xp.setImportImageMethod(Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD);
        xp.getChannelImages().insert(new ChannelImage("trans", "_REF"), new ChannelImage("fluo", ""));
        
        String[] files = new String[]{"/data/Images/Fluo/test"};
        Processor.importFiles(files, xp);
        assertEquals("number of fields detected", 1, xp.getMicroscopyFields().getChildCount());
        logger.info("imported field: name: {} image: timepoint: {} scale xy: {} scale z: {}", xp.getMicroscopyFields().getChildAt(0).getName(), xp.getMicroscopyFields().getChildAt(0).getImages().getTimePointNumber(), xp.getMicroscopyFields().getChildAt(0).getImages().getScaleXY(), xp.getMicroscopyFields().getChildAt(0).getImages().getScaleZ());
        //ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(xp.getMicroscopyField(0).getImages().getImage(0, 0));
        //ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(xp.getMicroscopyField(0).getImages().getImage(0, 1));
    }
    
    //@Test 
    public void testRotation() {
        testImport();
        //AutoRotationXY rot = new AutoRotationXY(-2, 2, 0.5, 0.05, InterpolationScheme.LINEAR);
        Image image = xp.getMicroscopyField(0).getImages().getImage(0, 0);
        Image sinogram = RadonProjection.getSinogram(image, -90, 90, 1, 1024);
        ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(image);
        ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(sinogram);
        //float angle = (float)RadonProjection.computeRotationAngleXY(image, (int)(image.getSizeZ()/2), 0, 360, 10, 0.1);
    }
}
