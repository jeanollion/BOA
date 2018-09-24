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

import static boa.test_utils.TestUtils.logger;
import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.core.Processor;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.input_image.InputImagesImpl;
import boa.data_structure.image_container.MemoryImageContainer;
import boa.image.Image;
import boa.image.io.ImageReader;
import boa.plugins.PluginFactory;
import boa.plugins.plugins.transformations.CropMicrochannelsFluo2D;
import boa.plugins.plugins.transformations.ImageStabilizerXY;

/**
 *
 * @author jollion
 */
public class TestStabilizerFromSavedPreProcessed {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        String dir = "/data/Images/Fluo/MopStab/160501_18_c0_small.tif";
        Image<? extends Image> im = ImageReader.openImage(dir);
        Image[][] imCT = new Image[1][im.sizeZ()];
        int z = 0;
        for (Image i : im.splitZPlanes()) imCT[0][z++] = i;
        MemoryImageContainer cont = new MemoryImageContainer(imCT);
        InputImagesImpl in = cont.getInputImages("18");
        logger.debug("Frames: {}", in.getFrameNumber());
        ImageStabilizerXY stab = new ImageStabilizerXY(1, 1000, 1e-12, 20).setAdditionalTranslation(1, 1, 1);//.setCropper(new CropMicrochannelsFluo2D(410, 45, 200, 0.5, 10));
        ImageStabilizerXY.debug=true;
        
        stab.computeConfigurationData(0, in);
        Image[][] imCTOut = new Image[1][im.sizeZ()];
        for (int t = 0; t<imCT[0].length; ++t) imCTOut[0][t] = stab.applyTransformation(0, t, imCT[0][t]);
        ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("before stab", imCT);
        ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("after stab", imCTOut);
    }
}
