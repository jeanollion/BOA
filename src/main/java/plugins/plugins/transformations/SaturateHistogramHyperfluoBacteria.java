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
package plugins.plugins.transformations;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObject;
import ij.process.AutoThresholder;
import image.BlankMask;
import image.Image;
import image.ImageByte;
import image.ImageInteger;
import image.ImageOperations;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import plugins.Thresholder;
import plugins.Transformation;
import plugins.plugins.thresholders.BackgroundFit;
import static plugins.plugins.thresholders.BackgroundFit.smooth;
import plugins.plugins.thresholders.BackgroundThresholder;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.IJAutoThresholder;
import utils.ArrayUtil;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SaturateHistogramHyperfluoBacteria implements Transformation {
    NumberParameter imageNumber = new BoundedNumberParameter("Number of images per group", 0, 1, 1, null);
    NumberParameter foregroundProportion = new BoundedNumberParameter("Hyperfluorecent cells foreground proportion threshold", 2, 0.3, 0, 1); 
    Parameter[] parameters = new Parameter[]{foregroundProportion, imageNumber};
    ArrayList<Double> configData = new ArrayList<>(2);
    
    public SaturateHistogramHyperfluoBacteria setForegroundProportion(double proportion) {
        this.foregroundProportion.setValue(proportion);
        return this;
    }
    
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        configData.clear();
        List<Image> allImages = new ArrayList<>();
        List<Image> imageTemp = new ArrayList<>();
        int imageN = imageNumber.getValue().intValue()-1;
        int tpMax = inputImages.getFrameNumber();
        int count =0;
        for (int t = 0; t<tpMax; ++t) {
            Image im = inputImages.getImage(channelIdx, t);
            if (im.getSizeZ()>1) imageTemp.addAll(im.splitZPlanes());
            else imageTemp.add(im);
            if (count==imageN) {
                count=0;
                allImages.add(Image.mergeZPlanes(imageTemp));
                imageTemp.clear();
            } else ++count;
        }
        
        double pThld = foregroundProportion.getValue().doubleValue();
        long t0 = System.currentTimeMillis();
        Image[] images = allImages.toArray(new Image[0]);
        Double[] thlds = new Double[images.length];
        ThreadRunner.execute(images, false, (Image object, int idx, int threadIdx) -> {thlds[idx] = getThld(object, pThld);});
        double thldMin = Arrays.stream(thlds).min((d1, d2)->Double.compare(d1, d2)).get();
        long t1 = System.currentTimeMillis();
        //logger.debug("saturate auto: {}ms", t1-t0);
        configData.add(thldMin);
        
        logger.debug("SaturateHistoAuto: {}", Utils.toStringList(configData));
    }
    static boolean shown = false;
    private static double getThld(Image im, double proportionThld) {
        double[] mm = im.getMinAndMax(null);
        int[] histo = im.getHisto256(mm[0], mm[1], null, null);
        boolean bi = im instanceof ImageByte;
        //double thldBack = BackgroundThresholder.runThresholder(histo, mm, bi, 2.5, 3, 3, null);
        double thldBack = BackgroundThresholder.runThresholder(im, null, 2.5, 3, 3, null);
        double thldHyper = IJAutoThresholder.runThresholder(AutoThresholder.Method.Otsu, histo, mm, bi);
        int idxBack = bi?(int)thldBack : IJAutoThresholder.convertTo256Threshold(thldBack, mm);
        if (!shown && idxBack==0) {
            ImageWindowManagerFactory.showImage(im.duplicate());
            shown=true;
        }
        int idxHyper = bi?(int)thldHyper : IJAutoThresholder.convertTo256Threshold(thldHyper, mm);
        double countHyper = 0, count = 0;
        for (int i = idxBack; i<=255; ++i) count+=histo[i];
        for (int i = idxHyper; i<=255; ++i) countHyper+=histo[i];
        //logger.debug("thldBack:{}({})({}) hyper: {}({}), count back: {}, hyper: {}, prop: {}", thldBack, idxBack, thldBack, thldHyper, idxHyper, count, countHyper, countHyper / count);
        double proportion = countHyper / count;
        if (proportion<proportionThld) return thldHyper;
        else return Double.POSITIVE_INFINITY;
    }

    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return configData.size()==1;
    }
    

    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (Double.isInfinite(configData.get(0))) return image;
        SaturateHistogram.saturate(configData.get(0), configData.get(0), image);
        return image;
    }

    @Override
    public ArrayList getConfigurationData() {
        return configData;
    }

    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.SAME;
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
