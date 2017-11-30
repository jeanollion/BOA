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

import static TestUtils.TestUtils.logger;
import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Task;
import dataStructure.configuration.MicroscopyField;
import dataStructure.containers.InputImages;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import ij.ImageJ;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import plugins.plugins.preFilter.IJSubtractBackground;
import processing.ImageTransformation;
import utils.ArrayUtil;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class GenerateFilms {
    public static void main(String[] args) {
        new ImageJ();
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
        /*String dbName = "boa_fluo160428";
        int[] fields = new int[]{6};
        int tStart = 0;
        int tEnd = 680;
        BoundingBox cropBB = null;
        float saturateChannel1 = 1;
        */
        /*String dbName = "fluo170515_MutS";
        int[] fields = new int[]{7};
        int tStart = 20;
        int tEnd = 340;
        BoundingBox cropBB = new BoundingBox(5, 974, 5, 380, 0, 0);
        double saturateChannel[]= new double[]{80, 0.99999};
        */
        
        String dbName = "fluo170528_uvrD";
        int[] fields = new int[]{1};
        int tStart = 100;
        int tEnd = 300;
        BoundingBox cropBB = new BoundingBox(240, 950, 20, 400, 0, 0);
        double saturateChannel[]= new double[]{50, 100};
        
        
        //Image[][] raw = getRawImagesAsFilmTF(dbName, 100, 200, 0, fields);
        //disp.showImage5D(dbName, raw);
        
        Image[][][] imageFTC = getPreProcessedImagesAsFilmFTC(dbName, tStart, tEnd, new int[]{0, 2}, fields);
        //int count=  0;
        //for (int f : fields) disp.showImage5D(dbName+" field: "+f, imageFTC[count++]);
        
        arrangeFilm(imageFTC[0], cropBB, saturateChannel);
        
        
        /*Image[][] film = generateMicrochannelFilm("boa_PHASE", 0, 8, new BoundingBox(-45, 80, -60, 615, 0, 1));
        Utils.apply(film, film, a -> Utils.apply(a, a, i -> ImageTransformation.flip(i, ImageTransformation.Axis.X)));
        ImageWindowManagerFactory.getImageManager().getDisplayer().showImage5D("mc8", film);
        */
    }
    
    
    
    private static void arrangeFilm(Image[][] imageTC, BoundingBox cropBB, double[] saturateChannel) {
        
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
            
            double scale = 255 / (saturateChannel[c] - minAndMax[0]);
            if (saturateChannel[c]<1) { // sturate image
                List<Image> planes = new ArrayList<>(imageTC.length);
                for (int t = 0; t<imageTC.length; ++t) planes.add(imageTC[t][c]);
                double max = ImageOperations.getPercentile(Image.mergeZPlanes(planes), null, null, saturateChannel[c])[0];
                logger.debug("percentile:{} value = {}", 1-saturateChannel[c], max);
                scale = 255 / (max - minAndMax[0]);
            } 
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
        ImageWindowManagerFactory.showImage(res);
    }
    public static Image[][][] getPreProcessedImagesAsFilmFTC(String dbName, int tStart, int tEnd, int[] structureIdx, int... fieldIndices) {
        if (tEnd<=tStart) throw new IllegalArgumentException("TEnds should be > tStart");
        MasterDAO db = new Task(dbName).getDB();
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
        MasterDAO db = new Task(dbName).getDB();
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
    
    public static Image[][] generateMicrochannelFilm(String dbName, int positionIdx, int mcIdx, BoundingBox bb) {
        MasterDAO db = new Task(dbName).getDB();
        ObjectDAO dao = MasterDAO.getDao(db, positionIdx);
        List<StructureObject> roots = dao.getRoots();
        Map<StructureObject, List<StructureObject>> mcs = StructureObjectUtils.getAllTracks(roots, 0);
        
        for (StructureObject o : mcs.keySet()) if (o.getIdx()==mcIdx) {
            List<StructureObject> mc = mcs.get(o);
            Image[][] resTC = new Image[mc.size()][db.getExperiment().getChannelImageCount()];
            for (int s = 0; s<db.getExperiment().getStructureCount(); ++s) {
                int c = db.getExperiment().getStructure(s).getChannelImage();
                for (StructureObject m : mc) {
                    Image rootImage = m.getRoot().getRawImage(s);
                    resTC[m.getFrame()][c] = rootImage.crop(bb.duplicate().translate(m.getBounds()));
                }
            }
            return resTC;
        }
        return null;
    }
    public static void homogenizeBB(Image[][] image) {
        BoundingBox bb = image[0][0].getBoundingBox().translateToOrigin();
        for (int i = 0; i<image.length; ++i) {
            for (int j = 0; j<image[0].length; ++j) {
                BoundingBox otherBB = image[i][j].getBoundingBox();
                bb = bb.expand(otherBB.getSizeX(), otherBB.getSizeY(), otherBB.getSizeZ());
            }
        }
        for (int i = 0; i<image.length; ++i) {
            for (int j = 0; j<image[0].length; ++j) {
                image[i][j] = image[i][j].crop(bb);
            }
        }
    }
}
