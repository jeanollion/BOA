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
package processing.dataGeneration;

import static TestUtils.TestUtils.logger;
import boa.gui.imageInteraction.IJImageWindowManager;
import boa.gui.imageInteraction.IJImageWindowManager.Roi3D;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Task;
import dataStructure.containers.InputImagesImpl;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MasterDAOFactory;
import dataStructure.objects.Region;
import ij.ImageJ;
import ij.ImagePlus;
import ij.process.AutoThresholder;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFloat;
import image.ImageFormat;
import image.ImageInteger;
import image.ImageOperations;
import image.ImageReader;
import image.ImageWriter;
import image.ThresholdMask;
import image.TypeConverter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import plugins.PluginFactory;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.transformations.AutoRotationXY;
import plugins.plugins.transformations.CropMicroChannelFluo2D;
import plugins.plugins.transformations.RemoveStripesSignalExclusion;
import plugins.plugins.transformations.SaturateHistogramHyperfluoBacteria;
import processing.Filters;
import processing.ImageTransformation;
import processing.ImageTransformation.Axis;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class AnalyseHyperFluoBacterias {

    public static void main(String[] args) {
        new ImageJ();
        PluginFactory.findPlugins("plugins.plugins");
        String path = "/data/Images/MOP/";
        //String[] xps = new String[]{"fluo160428", "fluo160501", "fluo170515_MutS", "fluo170517_MutH"};
        String[] xps = new String[]{"fluo160408_MutH"};
        String name = Utils.toStringArray(xps);
        //generateAndPreprocessDataset(10, path+File.separator+xps[0], xps);
        //Image dataSet = readDataset(path+File.separator+xps[0], name, true);
        
        Image dataSet = readDataset(path, "Control", true);
        
        //testThresholder(ultra, control, true);
        testCropMicrochannels(dataSet.splitZPlanes(), name);
        //testSaturate(ultra, control);
    }
    
    
    private static Image removeStripes(Image input) {
        return RemoveStripesSignalExclusion.removeStripes(input, input, BackgroundThresholder.runThresholder(input, null, 2.5, 3, 3, Double.MAX_VALUE, null), false);
    }
    private static void testSaturate(List<Image> ultra, String name) {
        List<ImageByte> ultraThld = Utils.transform(ultra, i->saturateAndThld(i));
        ImageWindowManagerFactory.showImage(Image.mergeZPlanes(ultraThld).setName(name+" thld"));
    }
    private static ImageByte saturateAndThld(Image image) {
        new SaturateHistogramHyperfluoBacteria().saturateHistogram(image);
        //double thld = IJAutoThresholder.runSimpleThresholder(image, null, null, AutoThresholder.Method.Otsu, 0);
        double thld = BackgroundThresholder.runThresholder(image, null, 3, 6, 3, Double.MAX_VALUE);
        return ImageOperations.threshold(image, thld, true, true);
    }
    /*private static void testSaturate() {
        String[] xps = new String[]{"fluo160428", "fluo160501", "fluo170515_MutS", "fluo170517_MutH"};
        String xp = xps[0];
        MasterDAO dao = new Task(xp).getDB();
        for (String p : dao.getExperiment().getPositionsAsString()) {
            logger.debug("XP: {}, Position: {}", xp, p);
            InputImagesImpl input = dao.getExperiment().getPosition(p).getInputImages();
            Transformation t=  new SaturateHistogramHyperfluoBacteria();
            t.computeConfigurationData(0, input);
            t.getConfigurationData()
            ++count;
            
        }
    }*/
    
    private static void testCropMicrochannels(List<Image> images, String name) {
        //CropMicroChannelFluo2D.debug=true;
        IJImageWindowManager iwm = (IJImageWindowManager)ImageWindowManagerFactory.getImageManager();
        if (images!=null && !images.isEmpty()) {
            List<ImageInteger> utlraCrop = Utils.transform(images, i->crop(i));
            Roi3D r = IJImageWindowManager.createRoi(Image.mergeZPlanes(utlraCrop), new BoundingBox(0, 0, 0), true);
            ImagePlus ip = (ImagePlus) ImageWindowManagerFactory.showImage(Image.mergeZPlanes(images).setName(name));
            iwm.displayObject(ip, r);
        }
    }
    
    private static Image preProcess(List<Image> images, String name) {
        images = Utils.transform(images, i->preProcess(i));
        return Image.mergeZPlanesResize(images, false).setName(name+"_PP");
    }
    private static Image preProcess(Image image) {
        image = ImageTransformation.flip(image, Axis.Y);
        image = removeStripes(image);
        new SaturateHistogramHyperfluoBacteria().saturateHistogram(image);
        return new AutoRotationXY().rotate(image);
    }
    private static ImageInteger crop(Image image) {
        
        BoundingBox bds = new CropMicroChannelFluo2D().setThresholder(new BackgroundThresholder(3, 6, 3)).getBoundingBox(image);
        //BoundingBox bds = CropMicroChannelFluo2D.getBoundingBox(image, 30, 0, 350, thld, 0.6, 200, 0, 0, 0, 0);
        logger.debug("Bds: {}", bds);
        ImageByte res=  new ImageByte("crop", image);
        ImageOperations.fill(res, 1, bds);
        return res;
    }
     
    private static void testThresholder(List<Image> images, String name, boolean hyper) {
        
        logger.debug("images count: {}", images.size());
        List<ImageInteger> masks = new ArrayList<>(images.size());
        List<Double> proportion = Utils.transform(images, i -> analyse(i, masks, hyper));
        Utils.plotProfile("Image proportion Utlra", Utils.toDoubleArray(proportion, false));
        ImageWindowManagerFactory.showImage(Image.mergeZPlanes(masks).setName(name+": masks"));
        logger.debug("Control");
        
    }
    private static double getThreshold(Image i) {
        return BackgroundThresholder.runThresholder(i, null, 3, 6, 3, Double.MAX_VALUE, null);
        //return BackgroundFit.backgroundFit(i, null, 3, null);
    }
    private static double analyse(Image i, List<ImageInteger> masks, boolean hyper) {
        double thld = getThreshold(i);
        ImageInteger tempMask = ImageOperations.threshold(i, thld, true, true, true, null);
        //Filters.binaryClose(tempMask, false, Filters.getNeighborhood(1, 0, i));
        ImageOperations.filterObjects(tempMask, tempMask, o->o.getSize()<=1);
        //Filters.open(tempMask, tempMask, Filters.getNeighborhood(2, 0, i));
        if (masks!=null) masks.add(tempMask);
        if (!hyper) {
            return tempMask.count() / i.getSizeXYZ();
        } else {
            double count = tempMask.count();
            double thld2 = IJAutoThresholder.runThresholder(i, null, null, AutoThresholder.Method.Otsu, 0);
            tempMask.getBoundingBox().translateToOrigin().loop((x, y, z)->{if (i.getPixel(x, y, z)>thld2) tempMask.setPixel(x, y, z, 2);});
            double count2 = new ThresholdMask(i, thld2, true, true).count();
            return count2/count;
        }
        
    }
    
    private static Image readDataset(String path, String name, boolean preProcessed) {
        String pp = preProcessed ? "_PP" : "";
        return ImageReader.openIJTif(path+File.separator+name+pp+".tif");
    }
    
    private static void generateAndPreprocessDataset(int framesPerPosition, String path, String... xps) {
        String name = Utils.toStringArray(xps);
        Image input = generateDataSet(framesPerPosition, xps);
        ImageWriter.writeToFile(input, path, name, ImageFormat.TIF);
        Image pp = preProcess(input.splitZPlanes(), name);
        ImageWriter.writeToFile(pp, path, name+"_PP", ImageFormat.TIF);
    }
    
    
    private static Image generateDataSet(int framesPerPosition, String... xps) {
        List<Pair<Integer, Image>> images = new ArrayList<>();
        for (String xp : xps) addImages(xp, framesPerPosition, images);
        Collections.sort(images, (p1, p2)->Integer.compare(p1.key, p2.key));
        Image all = Image.mergeZPlanes(Pair.unpairValues(images)).setName("AllImages");
        
        return all;
    }
    
    private static void addImages(String xp, int framePerPosition, List<Pair<Integer, Image>> images) {
        MasterDAO dao = new Task(xp).getDB();
        int count = 0;
        for (String p : dao.getExperiment().getPositionsAsString()) {
            logger.debug("XP: {}, Position: {}", xp, p);
            InputImagesImpl input = dao.getExperiment().getPosition(p).getInputImages();
            int interval = input.getFrameNumber() / framePerPosition;
            int off = input.getFrameNumber()%interval / 2;
            for (int f = off; f<input.getFrameNumber(); f+=interval) {
                Image im = input.getImage(0, f);
                double thld = IJAutoThresholder.runThresholder(im, null, AutoThresholder.Method.Otsu);
                int c = new ThresholdMask(im, thld, true, true).count();
                im.setName("t:"+thld+"/count:"+c);
                images.add(new Pair<>((int)c, im));
            }
            ++count;
            
        }
    }
}
