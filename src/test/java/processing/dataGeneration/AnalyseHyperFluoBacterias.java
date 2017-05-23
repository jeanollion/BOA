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
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Task;
import dataStructure.containers.InputImagesImpl;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MasterDAOFactory;
import ij.ImageJ;
import ij.process.AutoThresholder;
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
import plugins.plugins.thresholders.IJAutoThresholder;
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
        ImageWindowManagerFactory.showImage(dataSet[0]);
        ImageWindowManagerFactory.showImage(dataSet[1]);
        
        List<Image> ultra = dataSet[0].splitZPlanes();
        
        
    }
    
    private static void analyseImage(Image image, ImageInteger mask) {
        double thld = IJAutoThresholder.runThresholder(image, null, AutoThresholder.Method.Otsu);
        double[] msc = ImageOperations.getMeanAndSigma(image, null, v->v<thld);
        mask = ImageOperations.threshold(image, thld, false, true, true, mask);
        double thld2 = IJAutoThresholder.runThresholder(image, mask, AutoThresholder.Method.Otsu);
        double[] msc2 = ImageOperations.getMeanAndSigma(image, null, v->v<thld2);
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
