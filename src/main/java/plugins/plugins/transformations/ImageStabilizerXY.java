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
import plugins.Registration;
import static plugins.plugins.transformations.ImageStabilizerCore.combine;
import processing.ImageTransformation;

/**
 *
 * @author nasique
 */
public class ImageStabilizerXY implements Registration {
    // TODO affine
    TimePointParameter ref = new TimePointParameter("Reference time point", 50, false, false);
    ChoiceParameter transformationType = new ChoiceParameter("Transformation", new String[]{"Translation"}, "Translation", false); //, "Affine"
    ChoiceParameter pyramidLevel = new ChoiceParameter("Pyramid Level", new String[]{"0", "1", "2", "3", "4"}, "0", false);
    BoundedNumberParameter alpha = new BoundedNumberParameter("Template Update Coefficient [0-1]", 2, 1, 0, 1);
    BoundedNumberParameter maxIter = new BoundedNumberParameter("Maximum Iterations", 0, 200, 1, null);
    NumberParameter tol = new BoundedNumberParameter("Error Tolerance", 7, 1e-7, 0, null);
    Parameter[] parameters = new Parameter[]{ref, alpha, maxIter, tol}; //transformationType, pyramidLevel
    double[][][] translationTXY;
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
    
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        Image imageRef = inputImages.getImage(channelIdx, ref.getSelectedIndex());
        ImageProcessor[][] pyramids = ImageStabilizerCore.initWorkspace(imageRef.getSizeX(), imageRef.getSizeY(), pyramidLevel.getSelectedIndex());
        translationTXY = new double[1][inputImages.getTimePointNumber()][];
        FloatProcessor ipFloatRef = getFloatProcessor(imageRef, true);
        FloatProcessor trans=null;
        if (alpha.getValue().doubleValue()<1) trans  = new FloatProcessor(imageRef.getSizeX(), imageRef.getSizeY());
        translationTXY[0][ref.getSelectedIndex()] = new double[2];
        for (int t = ref.getSelectedIndex()-1; t>=0; --t) performCorrection(channelIdx, inputImages, t, ipFloatRef, pyramids, trans);
        if (alpha.getValue().doubleValue()<1 && ref.getSelectedIndex()>0) ipFloatRef = getFloatProcessor(imageRef, true); // reset template
        for (int t = ref.getSelectedIndex()+1; t<inputImages.getTimePointNumber(); ++t) performCorrection(channelIdx, inputImages, t, ipFloatRef, pyramids, trans);
    }
    
    private void performCorrection(int channelIdx, InputImages inputImages, int t, FloatProcessor ipFloatRef, ImageProcessor[][] pyramids,  FloatProcessor trans) {
        FloatProcessor currentTime = getFloatProcessor(inputImages.getImage(channelIdx, t), false);
        double[][] wp = ImageStabilizerCore.estimateTranslation(currentTime, ipFloatRef, pyramids[0], pyramids[1], maxIter.getValue().intValue(), tol.getValue().doubleValue());
        translationTXY[0][t] = new double[]{wp[0][0], wp[1][0]};
        logger.trace("ImageStabilizerXY: timepoint: {} dX: {} dY: {}", t, translationTXY[0][t][0], translationTXY[0][t][1]);
        //update template 
        if (alpha.getValue().doubleValue()<1) {
            ImageStabilizerCore.warpTranslation(trans, currentTime, wp);
            ImageStabilizerCore.combine(ipFloatRef, trans, alpha.getValue().doubleValue());
        }
    }
    
    private FloatProcessor getFloatProcessor(Image image, boolean duplicate) {
        if (image.getSizeZ()>1) image = image.getZPlane((int)(image.getSizeZ()/2.0+0.5)); //select middle slice only
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image);
        else if (duplicate) image = image.duplicate("");
        ImagePlus impRef = IJImageWrapper.getImagePlus(image);
        return (FloatProcessor)impRef.getProcessor();
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        logger.debug("stabilization time: {}, channel: {}, X:{}, Y:{}", timePoint, channelIdx, translationTXY[0][timePoint][0], translationTXY[0][timePoint][1]);
        if (translationTXY[0][timePoint][0]==0 && translationTXY[0][timePoint][1]==0) return image;
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image);
        ImagePlus impRef = IJImageWrapper.getImagePlus(image);
        ImageStack is = impRef.getImageStack();
        float[][] outPixels = new float[image.getSizeZ()][];
        FloatProcessor temp = new FloatProcessor(image.getSizeX(), image.getSizeY());
        double[][] wp = new double[][]{{translationTXY[0][timePoint][0]}, {translationTXY[0][timePoint][1]}};
        for (int z = 0; z<image.getSizeZ(); ++z) {
            ImageStabilizerCore.warpTranslation(temp, is.getProcessor(z+1), wp);
            outPixels[z]=(float[])temp.getPixels();
            temp = (FloatProcessor)is.getProcessor(z+1);
        }
        return new ImageFloat(image.getName(), image.getSizeX(), outPixels);
    }

    public Object[] getConfigurationData() {
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
    
}
