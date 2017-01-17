/*
 * Copyright (C) 2015 jollion
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

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.FilterSequence;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PreFilterSequence;
import static core.Processor.logger;
import dataStructure.containers.InputImage;
import dataStructure.containers.InputImages;
import static dataStructure.containers.InputImages.getAverageFrame;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import image.ImageFloat;
import image.TypeConverter;
import java.util.ArrayList;
import plugins.TransformationTimeIndependent;
import plugins.plugins.preFilter.IJSubtractBackground;
import processing.ImageTransformation;
import processing.ImageTransformation.InterpolationScheme;
import processing.RadonProjection;
import static processing.RadonProjection.getAngleArray;
import static processing.RadonProjection.radonProject;
import processing.neighborhood.EllipsoidalNeighborhood;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class AutoRotationXY implements TransformationTimeIndependent {
    //PreFilterSequence prefilters = new PreFilterSequence("Pre-Filters");
    NumberParameter minAngle = new BoundedNumberParameter("Minimal Angle for search", 2, -10, -90, 90);
    NumberParameter maxAngle = new BoundedNumberParameter("Maximal Angle for search", 2, 10, -90, 90);
    NumberParameter precision1 = new BoundedNumberParameter("Angular Precision of first seach", 2, 1, 0, null);
    NumberParameter precision2 = new BoundedNumberParameter("Angular Precision", 2, 0.1, 0, 1);
    //NumberParameter filterScale = new BoundedNumberParameter("Object Scale", 0, 15, 2, null); //TODO: conditional parameter
    ChoiceParameter interpolation = new ChoiceParameter("Interpolation", Utils.toStringArray(ImageTransformation.InterpolationScheme.values()), ImageTransformation.InterpolationScheme.BSPLINE5.toString(), false);
    ChoiceParameter searchMethod = new ChoiceParameter("Search method", SearchMethod.getValues(), SearchMethod.MAXVAR.getName(), false);
    NumberParameter refAverage = new BoundedNumberParameter("Number of frame to average around reference frame", 0, 0, 0, null);
    FilterSequence prefilters = new FilterSequence("Pre-Filters");
    Parameter[] parameters = new Parameter[]{searchMethod, minAngle, maxAngle, precision1, precision2, interpolation, refAverage, prefilters}; // prefilters -> problem : parent?
    ArrayList<Double> internalParams=new ArrayList<Double>(1);
    public static boolean debug = false;
    public AutoRotationXY(double minAngle, double maxAngle, double precision1, double precision2, InterpolationScheme interpolation, SearchMethod method) {
        this.minAngle.setValue(minAngle);
        this.maxAngle.setValue(maxAngle);
        this.precision1.setValue(precision1);
        this.precision2.setValue(precision2);
        if (interpolation!=null) this.interpolation.setSelectedItem(interpolation.toString());
        this.searchMethod.setSelectedItem(method.getName());
        //this.backgroundSubtractionRadius.setValue(backgroundSubtractionRadius);
    }
    public AutoRotationXY setPrefilters(plugins.Filter... filters) {
        prefilters.add(filters);
        return this;
    }
    public AutoRotationXY setAverageReference(int frameNumber) {
        this.refAverage.setValue(frameNumber);
        return this;
    }
    public AutoRotationXY() {}
    
    public boolean isTimeDependent() {
        return false;
    }

    public ArrayList<Double> getConfigurationData() {
        return internalParams;
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

    public void computeConfigurationData(int channelIdx, InputImages inputImages) {        
        Image image = getAverageFrame(inputImages, channelIdx, inputImages.getDefaultTimePoint(), refAverage.getValue().intValue());
        image = prefilters.filter(image);
        double angle=0;
        if (this.searchMethod.getSelectedItem().equals(SearchMethod.MAXVAR.getName())) { 
            angle = computeRotationAngleXY(image, image.getSizeZ()/2, minAngle.getValue().doubleValue(), maxAngle.getValue().doubleValue(), precision1.getValue().doubleValue(), precision2.getValue().doubleValue(), true, false, 0);
        } else if (this.searchMethod.getSelectedItem().equals(SearchMethod.MAXARTEFACT.getName())) {
            angle = computeRotationAngleXY(image, image.getSizeZ()/2, minAngle.getValue().doubleValue(), maxAngle.getValue().doubleValue(), precision1.getValue().doubleValue(), precision2.getValue().doubleValue(), false, true, 0);
        }
        
        //ImageFloat sin = RadonProjection.getSinogram(image, minAngle.getValue().doubleValue()+90, maxAngle.getValue().doubleValue()+90, precision1.getValue().doubleValue(), Math.min(image.getSizeX(), image.getSizeY())); //(int)Math.sqrt(image.getSizeX()*image.getSizeX() + image.getSizeY()*image.getSizeY())
        //new IJImageDisplayer().showImage(sin);
        internalParams = new ArrayList<Double>(1);
        internalParams.add(angle);
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (internalParams==null || internalParams.isEmpty()) throw new Error("Autorotation not configured");
        return ImageTransformation.rotateXY(TypeConverter.toFloat(image, null), internalParams.get(0), ImageTransformation.InterpolationScheme.valueOf(interpolation.getSelectedItem()), true);
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return internalParams!=null && !internalParams.isEmpty();
    }
    
    public static double[] computeRotationAngleXY(Image image, int z, double ang1, double ang2, double stepsize, float[] proj, boolean var, double filterScale) {

        // initial search
        double[] angles = getAngleArray(ang1, ang2, stepsize);
        double[] angleMax=new double[]{angles[0], angles[0]};
        double max=-1;
        ImageFloat sinogram = null;
        if (debug) sinogram = new ImageFloat("sinogram search angles: ["+ang1+";"+ang2+"]", angles.length, proj.length, 1);
        for (int angleIdx = 0; angleIdx<angles.length; ++angleIdx) { // first search
            radonProject(image, z, angles[angleIdx]+90, proj);
            if (debug) paste(proj, sinogram, angleIdx);
            //if (filterScale>0) filter(filterScale, proj);
            double tempMax = var?RadonProjection.var(proj):RadonProjection.max(proj);
            if (tempMax > max) {
                max = tempMax;
                angleMax[0] = angles[angleIdx];
                angleMax[1] = angles[angleIdx];
            } else if (tempMax==max) {
                angleMax[1] = angles[angleIdx];
            }
            //logger.trace("radon projection: computeRotationAngleXY: {}", angleMax);
        }
        if (debug) ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(sinogram);
        angleMax[0] = - angleMax[0];
        angleMax[1] = - angleMax[1];
        return angleMax;
    }
    
    public static double computeRotationAngleXY(Image image, int z , double ang1, double ang2, double stepsize1, double stepsize2, boolean var, boolean rotate90, double filterScale) {
        // first search:
        //float[] proj = new float[(int)Math.sqrt(image.getSizeX()*image.getSizeX() + image.getSizeY()*image.getSizeY())];
        float[] proj = new float[Math.min(image.getSizeX(),image.getSizeY())];
        double inc = rotate90?90:0;
        double[] firstSearch = computeRotationAngleXY(image, z, ang1+inc, ang2+inc, stepsize1, proj, var, filterScale);
        double[] secondSearch = computeRotationAngleXY(image, z, -firstSearch[0]-2*stepsize1, -firstSearch[1]+2*stepsize1, stepsize2, proj, var, filterScale);
        logger.debug("radon rotation search: first: {} second: {}", firstSearch, secondSearch);
        return (secondSearch[0]+secondSearch[1])/2d+inc;
    }
    
    private static void paste(float[] proj, ImageFloat image, int x) {
        for (int y = 0; y<proj.length; ++y) image.setPixel(x, y, 0, proj[y]);
    }
    
    private static void filter(double scale, float[] data) {
        ImageFloat im = new ImageFloat("", data.length, new float[][]{data});
        im = processing.Filters.median(im, im, new EllipsoidalNeighborhood(2, false));
        im = processing.Filters.tophat(im, im, new EllipsoidalNeighborhood(scale, false));
        float[] data2 = im.getPixelArray()[0];
        for (int i = 0; i<data.length; ++i) data[i] = data2[i];
    }
    
    public static enum SearchMethod {
        MAXVAR("Fluorecence Bactery Micro Channel"), // cherche le maximum variance. necessite de supprimer le bruit de fond
        MAXARTEFACT("Trans Bactery Micro Channel"); // se base sur l'artecfact d'imagerie, cherche la valeur max qui est a +90. 
        private final String name;
        SearchMethod(String name){this.name=name;}
        public static String[] getValues() {
            String[] values = new String[values().length]; int idx = 0;
            for (SearchMethod s : values()) values[idx++]=s.name;
            return values;
        }

        public String getName() {
            return name;
        }
        
    }
    
}
