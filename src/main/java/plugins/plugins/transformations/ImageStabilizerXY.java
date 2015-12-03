/*
 * Copyright (C) 2015 nasique
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
import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.TimePointParameter;
import dataStructure.containers.InputImages;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import image.IJImageWrapper;
import image.Image;
import image.ImageFloat;
import image.TypeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import plugins.Transformation;
import static plugins.plugins.transformations.ImageStabilizerCore.combine;
import static plugins.plugins.transformations.ImageStabilizerCore.gradient;
import processing.ImageTransformation;
import utils.ThreadRunner;
import utils.ThreadRunner.ThreadAction;

/**
 *
 * @author nasique
 */
public class ImageStabilizerXY implements Transformation {
    TimePointParameter ref = new TimePointParameter("Reference time point", 50, false, false);
    ChoiceParameter transformationType = new ChoiceParameter("Transformation", new String[]{"Translation"}, "Translation", false); //, "Affine"
    ChoiceParameter pyramidLevel = new ChoiceParameter("Pyramid Level", new String[]{"0", "1", "2", "3", "4"}, "0", false);
    BoundedNumberParameter alpha = new BoundedNumberParameter("Template Update Coefficient", 2, 1, 0, 1);
    BoundedNumberParameter maxIter = new BoundedNumberParameter("Maximum Iterations", 0, 200, 1, null);
    NumberParameter tol = new BoundedNumberParameter("Error Tolerance", 7, 1e-7, 0, null);
    Parameter[] parameters = new Parameter[]{ref, maxIter, tol}; //alpha, pyramidLevel
    ArrayList<ArrayList<Double>> translationTXY = new ArrayList<ArrayList<Double>>();
    
    public ImageStabilizerXY(){}
    
    public ImageStabilizerXY(int referenceTimePoint, int transformationType, int pyramidLevel, double templateUpdateCoeff, int maxIterations, double tolerance) {
        this.ref.setSelectedIndex(referenceTimePoint);
        this.transformationType.setSelectedIndex(transformationType);
        this.pyramidLevel.setSelectedIndex(pyramidLevel);
        this.alpha.setValue(templateUpdateCoeff);
        this.tol.setValue(tolerance);
        this.maxIter.setValue(maxIterations);
    }
    
    public ImageStabilizerXY setReferenceTimePoint(int timePoint) {
        this.ref.setSelectedIndex(timePoint);
        return this;
    }
    
    /*public void computeConfigurationData2(int channelIdx, InputImages inputImages) {
        Image imageRef = inputImages.getImage(channelIdx, 0);
        ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
        translationTXY = new ArrayList<Double[]>(inputImages.getTimePointNumber());
        FloatProcessor ipRef = getFloatProcessor(imageRef, false);
        FloatProcessor currentIp;
        translationTXY.add(new Double[2]);
        for (int t = 1; t<inputImages.getTimePointNumber(); ++t) {
            currentIp = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
            translationTXY[0][t] = performCorrection2(ipRef, currentIp, pyramids);
            ipRef = currentIp;
            logger.debug("ImageStabilizerXY: timepoint: {} dX: {} dY: {}", t, translationTXY[0][t][0], translationTXY[0][t][1]);
        }
        for (int t = 2; t<inputImages.getTimePointNumber(); ++t) {
            translationTXY[0][t][0]+=translationTXY[0][t-1][0];
            translationTXY[0][t][1]+=translationTXY[0][t-1][1];
        }
    }*/
    
    public void computeConfigurationData(final int channelIdx, final InputImages inputImages) {
        long tStart = System.currentTimeMillis();
        final int tRef = ref.getSelectedIndex();
        final Image imageRef = inputImages.getImage(channelIdx, tRef);
        
        
        final Double[][] translationTXYArray = new Double[inputImages.getTimePointNumber()][];
        final FloatProcessor ipFloatRef = getFloatProcessor(imageRef, true);
                
        /*ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
        FloatProcessor trans=null;
        if (alpha.getValue().doubleValue()<1) trans  = new FloatProcessor(imageRef.getSizeX(), imageRef.getSizeY());
        for (int t = ref.getSelectedIndex()-1; t>=0; --t) translationTXYArray[t] = performCorrection(channelIdx, inputImages, t, ipFloatRef, pyramids, trans);
        if (alpha.getValue().doubleValue()<1 && ref.getSelectedIndex()>0) ipFloatRef = getFloatProcessor(imageRef, true); // reset template
        for (int t = ref.getSelectedIndex()+1; t<inputImages.getTimePointNumber(); ++t) translationTXYArray[t] = performCorrection(channelIdx, inputImages, t, ipFloatRef, pyramids, trans);
        translationTXYArray[tRef] = new Double[]{0d, 0d};
        */
        // multithreaded version: only for alpha==1
        final ThreadRunner tr = new ThreadRunner(0, inputImages.getTimePointNumber());
        for (int i = 0; i<tr.threads.length; i++) {
            tr.threads[i] = new Thread(
                new Runnable() {
                    public void run() {
                        final ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
                        gradient(pyramids[1][0], ipFloatRef);
                        for (int t = tr.ai.getAndIncrement(); t<tr.end; t = tr.ai.getAndIncrement()) {
                            if (t==tRef) translationTXYArray[t] = new Double[]{0d, 0d};
                            else translationTXYArray[t] = performCorrection(channelIdx, inputImages, t, pyramids);
                        }
                    }
                }
            );
        }
        tr.startAndJoin();
        
        translationTXY = new ArrayList<ArrayList<Double>>(translationTXYArray.length);
        for (Double[] d : translationTXYArray) translationTXY.add(new ArrayList<Double>(Arrays.asList(d)));
        long tEnd = System.currentTimeMillis();
        logger.debug("ImageStabilizerXY: total estimation time: {}", tEnd-tStart);
    }
    
    private Double[] performCorrection(int channelIdx, InputImages inputImages, int t, ImageProcessor[][] pyramids) {
        long t0 = System.currentTimeMillis();
        FloatProcessor currentTime = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
        long tStart = System.currentTimeMillis();
        double[][] wp = ImageStabilizerCore.estimateTranslation(currentTime, null, pyramids[0], pyramids[1], false, maxIter.getValue().intValue(), tol.getValue().doubleValue());
        long tEnd = System.currentTimeMillis();
        Double[] res =  new Double[]{wp[0][0], wp[1][0]};
        logger.debug("ImageStabilizerXY: timepoint: {} dX: {} dY: {}, open & preProcess time: {}, estimate translation time: {}", t, res[0], res[1], tStart-t0, tEnd-tStart);
        return res;
    }
    
    private Double[] performCorrection(int channelIdx, InputImages inputImages, int t, FloatProcessor ipFloatRef, ImageProcessor[][] pyramids,  FloatProcessor trans) {
        long t0 = System.currentTimeMillis();
        FloatProcessor currentTime = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
        long tStart = System.currentTimeMillis();
        double[][] wp = ImageStabilizerCore.estimateTranslation(currentTime, ipFloatRef, pyramids[0], pyramids[1], true, maxIter.getValue().intValue(), tol.getValue().doubleValue());
        long tEnd = System.currentTimeMillis();
        Double[] res =  new Double[]{wp[0][0], wp[1][0]};
        logger.debug("ImageStabilizerXY: timepoint: {} dX: {} dY: {}, open & preProcess time: {}, estimate translation time: {}", t, res[0], res[1], tStart-t0, tEnd-tStart);
        //update template 
        if (alpha.getValue().doubleValue()<1) {
            ImageStabilizerCore.warpTranslation(trans, currentTime, wp);
            ImageStabilizerCore.combine(ipFloatRef, trans, alpha.getValue().doubleValue());
        }
        return res;
    }
    
    private FloatProcessor getFloatProcessor(Image image, boolean duplicate) {
        if (image.getSizeZ()>1) image = image.getZPlane((int)(image.getSizeZ()/2.0+0.5)); //select middle slice only
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image, null);
        else if (duplicate) image = image.duplicate("");
        ImagePlus impRef = IJImageWrapper.getImagePlus(image);
        return (FloatProcessor)impRef.getProcessor();
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        ArrayList<Double> trans = translationTXY.get(timePoint);
        //logger.debug("stabilization time: {}, channel: {}, X:{}, Y:{}", timePoint, channelIdx, trans.get(0), trans.get(1));
        if (trans.get(0)==0 && trans.get(1)==0) return image;
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image, null);
        /*ImagePlus impRef = IJImageWrapper.getImagePlus(image);
        ImageStack is = impRef.getImageStack();
        float[][] outPixels = new float[image.getSizeZ()][];
        FloatProcessor temp = new FloatProcessor(image.getSizeX(), image.getSizeY());
        double[][] wp = new double[][]{{translationTXY[0][timePoint][0]}, {translationTXY[0][timePoint][1]}};
        for (int z = 0; z<image.getSizeZ(); ++z) {
            ImageStabilizerCore.warpTranslation(temp, is.getProcessor(z+1), wp);
            outPixels[z]=(float[])temp.getPixels();
            temp = (FloatProcessor)is.getProcessor(z+1);
        }
        return new ImageFloat(image.getName(), image.getSizeX(), outPixels);*/
        return ImageTransformation.translate(image, -trans.get(0), -trans.get(1), 0, ImageTransformation.InterpolationScheme.BSPLINE5);
    }

    public ArrayList getConfigurationData() {
        return this.translationTXY;
    }

    public boolean isTimeDependent() {
        return true;
    }

    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }
    
}
