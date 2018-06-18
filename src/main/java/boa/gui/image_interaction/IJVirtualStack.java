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
package boa.gui.image_interaction;

import boa.gui.GUI;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.Position;
import boa.data_structure.dao.ImageDAO;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import boa.image.BlankMask;
import boa.image.MutableBoundingBox;
import boa.image.IJImageWrapper;
import boa.image.Image;
import static boa.image.Image.logger;
import java.awt.Font;
import java.awt.image.ColorModel;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 *
 * @author jollion
 */
public class IJVirtualStack extends VirtualStack {
    BiFunction<Integer, Integer, Image> imageOpenerCT;
    Function<Integer, int[]> getFCZ;
    int[] FCZCount;
    Image[][] imageCT;
    public IJVirtualStack(int sizeX, int sizeY, int[] FCZCount, Function<Integer, int[]> getFCZ, BiFunction<Integer, Integer, Image> imageOpenerCT) {
        super(sizeX, sizeY, null, null);
        this.imageOpenerCT=imageOpenerCT;
        this.getFCZ=getFCZ;
        this.FCZCount=FCZCount;
        this.imageCT=new Image[FCZCount[1]][FCZCount[0]];
        for (int n = 0; n<FCZCount[0]*FCZCount[1]*FCZCount[2]; ++n) super.addSlice("");
    }
    
    @Override
    public ImageProcessor getProcessor(int n) {
        int[] fcz = getFCZ.apply(n);
        if (imageCT[fcz[1]][fcz[0]]==null) {
            imageCT[fcz[1]][fcz[0]] = imageOpenerCT.apply(fcz[1], fcz[0]);
        }
        if (fcz[2]>=imageCT[fcz[1]][fcz[0]].sizeZ()) {
            if (imageCT[fcz[1]][fcz[0]].sizeZ()==1) fcz[2]=0; // case of reference images 
            else throw new IllegalArgumentException("Wrong Z size for channel: "+fcz[1]);
        }
        return IJImageWrapper.getImagePlus(imageCT[fcz[1]][fcz[0]].getZPlane(fcz[2])).getProcessor();
    }
    public static void openVirtual(Experiment xp, String position, boolean output) {
        Position f = xp.getPosition(position);
        int channels = xp.getChannelImageCount();
        int frames = f.getFrameNumber(false);
        Image[] bdsC = new Image[xp.getChannelImageCount()];
        for (int c = 0; c<bdsC.length; ++c) bdsC[c]= output ? xp.getImageDAO().openPreProcessedImage(c, 0, position) : f.getInputImages().getImage(c, 0);
        if (bdsC[0]==null) {
            GUI.log("No "+(output ? "preprocessed " : "input")+" images found for position: "+position);
            return;
        }
        logger.debug("scale: {}", bdsC[0].getScaleXY());
        // case of reference image with only one Z -> duplicate
        int maxZ = Collections.max(Arrays.asList(bdsC), (b1, b2)->Integer.compare(b1.sizeZ(), b2.sizeZ())).sizeZ();
        int[] fcz = new int[]{frames, channels, maxZ};
        BiFunction<Integer, Integer, Image> imageOpenerCT  = output ? (c, t) -> xp.getImageDAO().openPreProcessedImage(c, t, position) : (c, t) -> f.getInputImages().getImage(c, t);
        IJVirtualStack s = new IJVirtualStack(bdsC[0].sizeX(), bdsC[0].sizeY(), fcz, IJImageWrapper.getStackIndexFunctionRev(fcz), imageOpenerCT);
        ImagePlus ip = new ImagePlus();
        ip.setTitle((output ? "PreProcessed Images of position: " : "Input Images of position: ")+position);
        ip.setStack(s, channels,maxZ, frames);
        ip.setOpenAsHyperStack(true);
        Calibration cal = new Calibration();
        cal.pixelWidth=bdsC[0].getScaleXY();
        cal.pixelHeight=bdsC[0].getScaleXY();
        cal.pixelDepth=bdsC[0].getScaleZ();
        ip.setCalibration(cal);
        ip.show();
        ImageWindowManagerFactory.getImageManager().addLocalZoom(ip.getCanvas());
        ImageWindowManagerFactory.getImageManager().addInputImage(position, ip, !output);
    }
}
