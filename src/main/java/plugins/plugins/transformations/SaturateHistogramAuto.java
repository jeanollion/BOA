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
import plugins.Thresholder;
import plugins.Transformation;
import plugins.plugins.thresholders.BackgroundFit;
import static plugins.plugins.thresholders.BackgroundFit.smooth;
import plugins.plugins.thresholders.ConstantValue;
import plugins.plugins.thresholders.IJAutoThresholder;
import utils.ArrayUtil;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SaturateHistogramAuto implements Transformation {
    PluginParameter<Thresholder> threshold = new PluginParameter<>("Threshold (separation from background)", Thresholder.class, new IJAutoThresholder().setMethod(AutoThresholder.Method.Otsu), false);
    NumberParameter sigmaStart = new BoundedNumberParameter("Saturate value start (sigma)", 1, 1, 0, null); 
    NumberParameter sigmaEnd = new BoundedNumberParameter("Saturate value start (end)", 1, 2, 0, null);
    Parameter[] parameters = new Parameter[]{threshold, sigmaStart, sigmaEnd};
    ArrayList<Double> configData = new ArrayList<>(2);;
    
    public SaturateHistogramAuto setSigmas(double sigmaStart, double sigmeEnd) {
        this.sigmaEnd.setValue(sigmeEnd);
        this.sigmaStart.setValue(sigmaStart);
        return this;
    }
    
    @Override
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        int tpMax = inputImages.getTimePointNumber();
        double[] back = new double[3];
        double[] fore = new double[3];
        for (int t = 0; t<tpMax; ++t) {
            Image im = inputImages.getImage(channelIdx, t);
            add(im, t, back, fore);
        }
        computeMeanSigma(back);
        computeMeanSigma(fore);
        double diff= fore[0] - back[0];
        configData.clear();
        configData.add(fore[0]+diff*sigmaStart.getValue().doubleValue());
        configData.add(fore[0]+diff*sigmaEnd.getValue().doubleValue());
        /*double[] mmBack = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] mmFore = new double[]{Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        double[] thldArray = new double[tpMax];
        for (int t = 0; t<tpMax; ++t) {
            Image im = inputImages.getImage(channelIdx, t);
            getThldAndMM(im, t, thldArray, mmBack, mmFore);
        }
        double thld = ArrayUtil.meanSigma(thldArray, 0, tpMax, null)[0];
        mmBack[1]=thld;
        mmFore[0]=thld;
        int[] histBack = new int[256];
        int[] histFore = new int[256];
        ImageByte mask = new ImageByte("mask", inputImages.getImage(channelIdx, 0));
        for (int t = 0; t<tpMax; ++t) {
            Image im = inputImages.getImage(channelIdx, t);
            addHisto(im, mask, t, thld, histBack, histFore, mmBack, mmFore);
        }
        double thldB = BackgroundFit.backgroundFitHalf(histBack, mmBack, inputImages.getImage(channelIdx, 0) instanceof ImageByte, 0, null);
        double thldF = BackgroundFit.backgroundFitHalf(histFore, mmFore, inputImages.getImage(channelIdx, 0) instanceof ImageByte, 0, null);
        double diff = thldB-thldF;
        configData.clear();
        configData.add(thldF+diff*sigmaStart.getValue().doubleValue());
        configData.add(thldF+diff*sigmaEnd.getValue().doubleValue());*/
        logger.debug("SaturateHistoAuto: {}", Utils.toStringList(configData));
    }

    @Override
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return configData.size()==2;
    }
    private void getThldAndMM(Image image, int tp, double[] thldArray, double[] mmBack, double[] mmFore) {
        StructureObject o = new StructureObject(tp, new BlankMask("", image), null);
        double thld = threshold.instanciatePlugin().runThresholder(image, o);
        thldArray[tp]=thld;
        double[] mm = image.getMinAndMax(null);
        mmBack[0] = Math.min(mmBack[0], mm[0]);
        mmFore[1] = Math.max(mmFore[1], mm[1]);
    }
    private void addHisto(Image image, ImageInteger mask, int timePoint, double thld, int[] histoBack, int[] histoFore, double[] mmBack, double[] mmFore) {
        ImageOperations.threshold(image, thld, true, true, true, mask);
        int[] histF = image.getHisto256(mmFore[0], mmFore[1], mask, null);
        ImageOperations.addHisto(histF, histoFore, false);
        ImageOperations.threshold(image, thld, false, true, true, mask);
        int[] histB = image.getHisto256(mmBack[0], mmBack[1], mask, null);
        ImageOperations.addHisto(histB, histoBack, false);
    }
    private void add(Image image, int timePoint, double[] mscBack, double[] mscFore) {
        StructureObject o = new StructureObject(timePoint, new BlankMask("", image), null);
        double thld = threshold.instanciatePlugin().runThresholder(image, o);
        double[] meanSigma = ImageOperations.getMeanAndSigma(image, null, d -> d>=thld);
        meanSigma[1] = (meanSigma[1]*meanSigma[1] + meanSigma[0]*meanSigma[0])*meanSigma[2];
        meanSigma[0]*=meanSigma[2];
        mscFore[0]+=meanSigma[0];
        mscFore[1]+=meanSigma[1];
        mscFore[2]+=meanSigma[2];
        meanSigma = ImageOperations.getMeanAndSigma(image, null, d -> d<thld);
        meanSigma[1] = (meanSigma[1]*meanSigma[1] + meanSigma[0]*meanSigma[0])*meanSigma[2];
        meanSigma[0]*=meanSigma[2];
        mscBack[0]+=meanSigma[0];
        mscBack[1]+=meanSigma[1];
        mscBack[2]+=meanSigma[2];
    }
    private void computeMeanSigma(double[] array) {
        array[0]/=array[2];
        array[1] = Math.sqrt(array[1]/array[2] - array[0]*array[0]);
    }

    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        /*StructureObject o = new StructureObject(timePoint, new BlankMask("", image), null);
        double thld = threshold.instanciatePlugin().runThresholder(image, o);
        double[] meanSigma = ImageOperations.getMeanAndSigma(image, null, d -> d>=thld);
        double thldStart = meanSigma[0] + sigmaStart.getValue().doubleValue() * meanSigma[1];
        double thldEnd = meanSigma[0] + sigmaEnd.getValue().doubleValue() * meanSigma[1];
        //logger.debug("StaurateHistoAuto: c={}, f={}, thld:{}, thldStart: {}, thldEnd: {}", channelIdx, timePoint, thld,thldStart, thldEnd);
        */
        SaturateHistogram.saturate(configData.get(0), configData.get(1), image);
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
