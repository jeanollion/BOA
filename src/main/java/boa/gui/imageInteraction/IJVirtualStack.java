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
package boa.gui.imageInteraction;

import boa.gui.GUI;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.ImageDAO;
import ij.ImagePlus;
import ij.VirtualStack;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import image.BlankMask;
import image.BoundingBox;
import image.IJImageWrapper;
import image.Image;
import static image.Image.logger;
import java.awt.Font;
import java.awt.image.ColorModel;
import java.io.File;
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
        return IJImageWrapper.getImagePlus(imageCT[fcz[1]][fcz[0]].getZPlane(fcz[2])).getProcessor();
    }
    public static void openVirtual(Experiment xp, String position, boolean output) {
        MicroscopyField f = xp.getPosition(position);
        int channels = xp.getChannelImageCount();
        int frames = f.getTimePointNumber(false);
        Image bds= output ? xp.getImageDAO().getPreProcessedImageProperties(position) : f.getInputImages().getImage(0, 0);
        if (bds==null) {
            GUI.log("No "+(output ? "preprocessed " : "input")+" images found for position: "+position);
            return;
        }
        int[] fcz = new int[]{frames, channels, bds.getSizeZ()};
        BiFunction<Integer, Integer, Image> imageOpenerCT  = output ? (c, t) -> xp.getImageDAO().openPreProcessedImage(c, t, position) : (c, t) -> f.getInputImages().getImage(c, t);
        IJVirtualStack s = new IJVirtualStack(bds.getSizeX(), bds.getSizeY(), fcz, IJImageWrapper.getStackIndexFunctionRev(fcz), imageOpenerCT);
        ImagePlus ip = new ImagePlus();
        ip.setTitle((output ? "PreProcessed Images of position: " : "Input Images of position: ")+position);
        ip.setStack(s, channels, bds.getSizeZ(), frames);
        ip.setOpenAsHyperStack(true);
        Calibration cal = new Calibration();
        cal.pixelWidth=bds.getScaleXY();
        cal.pixelHeight=bds.getScaleXY();
        cal.pixelDepth=bds.getScaleZ();
        ip.setCalibration(cal);
        ip.show();
    }
}
