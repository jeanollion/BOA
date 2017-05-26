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

import static TestUtils.Utils.logger;
import boa.gui.imageInteraction.IJImageWindowManager;
import boa.gui.imageInteraction.IJImageWindowManager.Roi3D;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Task;
import dataStructure.containers.InputImagesImpl;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MasterDAOFactory;
import dataStructure.objects.Object3D;
import ij.ImageJ;
import ij.process.AutoThresholder;
import image.BoundingBox;
import image.Image;
import image.ImageByte;
import image.ImageFormat;
import image.ImageInteger;
import image.ImageOperations;
import image.ImageReader;
import image.ImageWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import plugins.plugins.thresholders.BacteriaFluoThresholder;
import plugins.plugins.thresholders.IJAutoThresholder;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.transformations.CropMicroChannelFluo2D;
import plugins.plugins.transformations.Flip;
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
        String path = "/data/Images/MOP/";
        //generateDataSet(path);
        Image[] dataSet = readDataset(path);
        
        
        
        List<Image> ultra = dataSet[0].splitZPlanes();
        List<Image> control = dataSet[1].splitZPlanes();
        Collections.shuffle(control);
        control = control.subList(0, ultra.size());
        dataSet[1] = Image.mergeZPlanes(control);
        
        dataSet[0]=ImageTransformation.flip(dataSet[0], Axis.Y);
        dataSet[1]=ImageTransformation.flip(dataSet[1], Axis.Y);
        
        ImageWindowManagerFactory.showImage(dataSet[0]);
        ImageWindowManagerFactory.showImage(dataSet[1]);
        
        
        //testThresholder(ultra, control);
        testCropMicrochannels(dataSet[0], dataSet[1]);
    }
    
    private static void testCropMicrochannels(Image ultra, Image control) {
        IJImageWindowManager iwm = (IJImageWindowManager)ImageWindowManagerFactory.getImageManager();
        List<Image> ultraZ = ultra.splitZPlanes();
        List<ImageInteger> utlraCrop = Utils.apply(ultraZ, i->crop(i));
        Roi3D r = IJImageWindowManager.createRoi(Image.mergeZPlanes(utlraCrop), new BoundingBox(0, 0, 0), true);
        iwm.displayObject(iwm.getDisplayer().getImage(ultra), r);
        ImageWindowManagerFactory.showImage(Image.mergeZPlanes(utlraCrop).setName("ultra"));
        //if (true) return;
        List<Image> controlZ = control.splitZPlanes();
        List<ImageInteger> ctrlCrop = Utils.apply(controlZ, i->crop(i));
        Roi3D rC = IJImageWindowManager.createRoi(Image.mergeZPlanes(ctrlCrop), new BoundingBox(0, 0, 0), true);
        iwm.displayObject(iwm.getDisplayer().getImage(control), rC);
        ImageWindowManagerFactory.showImage(Image.mergeZPlanes(ctrlCrop).setName("ctrl"));
    }
    
    private static ImageInteger crop(Image image) {
        BoundingBox bds = CropMicroChannelFluo2D.getBoundingBox(image, 30, 0, 330, getThreshold(image), 0.6, 200, 0, 0, 0, 0);
        //logger.debug("Bds: {}", bds);
        ImageByte res=  new ImageByte("crop", image);
        ImageOperations.fill(res, 1, bds);
        return res;
    }
     
    private static void testThresholder(List<Image> ultra, List<Image> control) {
        
        logger.debug("images count: {}", ultra.size());
        List<ImageInteger> masks = new ArrayList<>(ultra.size());
        List<Double> proportion = Utils.apply(ultra, i -> analyse(i, masks));
        Utils.plotProfile("Image proportion Utlra", Utils.toDoubleArray(proportion, false));
        ImageWindowManagerFactory.showImage(Image.mergeZPlanes(masks).setName("Ultra: masks"));
        logger.debug("Control");
        
        masks.clear();
        List<Double> proportionC = Utils.apply(control, i -> analyse(i, masks));
        ImageWindowManagerFactory.showImage(Image.mergeZPlanes(masks).setName("Ctrl: masks"));
        Utils.plotProfile("Image proportion Control", Utils.toDoubleArray(proportionC, false));
    }
    private static double getThreshold(Image i) {
        return BackgroundThresholder.run(i, null, 2.5, 3, 3, null);
    }
    
    private static double analyse(Image i, List<ImageInteger> masks) {
        double thld = getThreshold(i);
        ImageInteger tempMask = ImageOperations.threshold(i, thld, false, true, true, null);
        masks.add(tempMask.duplicate());
        //double thld = BacteriaFluoThresholder.getThreshold(i, 0.2, null, tempMask, masks, masks2);
        return ImageOperations.getMeanAndSigma(i, null,  v->v>thld)[2] / i.getSizeXYZ();
        
    }
    
    private static Image[] readDataset(String path) {
        Image uf = ImageReader.openIJTif(path+File.separator+"UltraFluo.tif");
        Image ctrl = ImageReader.openIJTif(path+File.separator+"Control.tif");
        return new Image[]{uf, ctrl};
    }
    private static void generateDataSet(String path) {
        String[] xps = new String[]{"fluo160428", "fluo160501", "fluo170515_MutS", "fluo170517_MutH"};
        List<Pair<Integer, Image>> images = new ArrayList<>();
        for (String xp : xps) addImages(xp, 10, images);
        Collections.sort(images, (p1, p2)->Integer.compare(p1.key, p2.key));
        Image all = Image.mergeZPlanes(Pair.unpairValues(images)).setName("AllImages");
        ImageWriter.writeToFile(all, path, all.getName(), ImageFormat.TIF);
        ImageWindowManagerFactory.showImage(all);
    }
    
    private static void addImages(String xp, int framePerPosition, List<Pair<Integer, Image>> images) {
        MasterDAO dao = new Task(xp).getDB();
        int count = 0;
        for (String p : dao.getExperiment().getPositionsAsString()) {
            logger.debug("XP: {}, Position: {}", xp, p);
            InputImagesImpl input = dao.getExperiment().getPosition(p).getInputImages();
            int interval = input.getFrameNumber() / framePerPosition;
            for (int f = 0; f<input.getFrameNumber(); f+=interval) {
                Image im = input.getImage(0, f);
                double thld = IJAutoThresholder.runThresholder(im, null, AutoThresholder.Method.Otsu);
                double[] msc = ImageOperations.getMeanAndSigma(im, null, v->v>thld);
                if (msc[2]>50000) continue;
                im.setName("t:"+thld+"/count:"+msc[2]);
                images.add(new Pair<>((int)msc[2], im));
            }
            ++count;
            
        }
    }
}
