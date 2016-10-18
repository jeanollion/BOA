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
package processing.dataGeneration;

import static TestUtils.Utils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.InputImages;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import plugins.plugins.preFilter.IJSubtractBackground;
import utils.ArrayUtil;

/**
 *
 * @author jollion
 */
public class DisplayFilms {
    public static void main(String[] args) {
        // WT
        /*String dbName = "fluo160218";
        int[] fields = ArrayUtil.generateIntegerArray(8, 9);
        int tStart = 14;
        int tEnd = 70;
        BoundingBox cropBB = new BoundingBox(18, 936, 26, 396, 0, 0);
        float saturateChannel1 = 0.4f;
        */
        
        // mutH
        /*String dbName = "fluo151127";
        int[] fields = new int[]{25};
        int tStart = 49;
        int tEnd = 300;
        BoundingBox cropBB = new BoundingBox(29, 673, 33, 388, 0, 0);
        float saturateChannel1 = 0.6f;
        */
        
        /*String dbName = "fluo160218";
        int[] fields = ArrayUtil.generateIntegerArray(8, 9);
        int tStart = 14;
        int tEnd = 70;
        BoundingBox cropBB = new BoundingBox(18, 936, 26, 396, 0, 0);
        float saturateChannel1 = 0.4f;
        */
        String dbName = "boa_fluo160428";
        int[] fields = new int[]{6};
        int tStart = 0;
        int tEnd = 680;
        BoundingBox cropBB = null;
        float saturateChannel1 = 1;
        
        ImageDisplayer disp = new IJImageDisplayer();
        //Image[][] raw = getRawImagesAsFilmTF(dbName, 100, 200, 0, fields);
        //disp.showImage5D(dbName, raw);
        
        Image[][][] imageFTC = getPreProcessedImagesAsFilmFTC(dbName, tStart, tEnd, new int[]{0, 2}, fields);
        //int count=  0;
        //for (int f : fields) disp.showImage5D(dbName+" field: "+f, imageFTC[count++]);
        
        arrangeFilm(imageFTC[0], cropBB, saturateChannel1);
    }
    private static void arrangeFilm(Image[][] imageTC, BoundingBox cropBB, float saturateChannel1) {
        
        // normalize intensities 
        for (int c = 0; c<imageTC[0].length; ++c) {
            double[] minAndMax = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}; // get Min and max over time
            for (int t = 0; t<imageTC.length; ++t) {
                if (c==1) imageTC[t][c]=IJSubtractBackground.filter(imageTC[t][c], 15, false, false, true, false);
                if (cropBB!=null) imageTC[t][c] = imageTC[t][c].crop(cropBB).resetOffset();
                double[] minAndMaxTemp = imageTC[t][c].getMinAndMax(null);
                if (minAndMaxTemp[0]<minAndMax[0]) minAndMax[0] = minAndMaxTemp[0];
                if (minAndMaxTemp[1]>minAndMax[1]) minAndMax[1] = minAndMaxTemp[1];
            }
            double scale = 255 / (minAndMax[1] - minAndMax[0]);
            if (c==1) scale = 255 / (minAndMax[1]* saturateChannel1 - minAndMax[0]); // sturate image
            double offset = -minAndMax[0] * scale;
            for (int t = 0; t<imageTC.length; ++t) { // normalize & convert to 8 bit
                imageTC[t][c] = ImageOperations.affineOperation(imageTC[t][c], new ImageByte("", imageTC[t][c]), scale, offset);
            }
        }
        // paste images in Y
        Image res = Image.createEmptyImage("film", imageTC[0][0], new BlankMask("", imageTC[0][0].getSizeX(), imageTC[0][0].getSizeY()*imageTC[0].length, imageTC.length));
        int yOff= 0;
        for (int c = 0; c<imageTC[0].length; ++c) {
            for (int t = 0; t<imageTC.length; ++t) {
                
                ImageOperations.pasteImage(imageTC[t][c], res, new BoundingBox(0, yOff, t), null);
            }
            yOff += imageTC[0][0].getSizeY();
        }
        new IJImageDisplayer().showImage(res);
    }
    public static Image[][][] getPreProcessedImagesAsFilmFTC(String dbName, int tStart, int tEnd, int[] structureIdx, int... fieldIndices) {
        if (tEnd<=tStart) throw new IllegalArgumentException("TEnds should be > tStart");
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        if (fieldIndices==null || fieldIndices.length==0) fieldIndices = ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount());
        Image[][][] resFTC = new Image[fieldIndices.length][tEnd-tStart][structureIdx.length];
        int count = 0;
        for (int fieldNumber : fieldIndices) {
            MicroscopyField f = db.getExperiment().getPosition(fieldNumber);
            ObjectDAO dao = db.getDao(f.getName());
            for (int t = tStart; t<tEnd; ++t) {
                StructureObject root = dao.getRoots().get(t);
                if (root==null) logger.debug("root null for field: {} tp: {}", f.getName(), t);
                int countC = 0;
                for (int c : structureIdx) resFTC[count][t-tStart][countC++] = root.getRawImage(c);
            }
            count++;
        }
        return resFTC;
    }
    public static Image[][] getRawImagesAsFilmTF(String dbName, int tStart, int tEnd, int channelIdx, int... fieldIndices) {
        if (tEnd<=tStart) throw new IllegalArgumentException("TEnds should be > tStart");
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        if (fieldIndices==null || fieldIndices.length==0) fieldIndices = ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount());
        Image[][] resTF = new Image[tEnd-tStart][fieldIndices.length];
        int count = 0;
        for (int fieldNumber : fieldIndices) {
            MicroscopyField f = db.getExperiment().getPosition(fieldNumber);
            InputImages images = f.getInputImages();
            for (int t = tStart; t<tEnd; ++t) {
                resTF[t-tStart][count] = images.getImage(channelIdx, t);
            }
            count++;
        }
        return resTF;
    }
}
