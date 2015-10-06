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
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import static core.Processor.logger;
import dataStructure.containers.InputImage;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import image.ImageFloat;
import image.TypeConverter;
import java.util.ArrayList;
import plugins.TransformationTimeIndependent;
import plugins.plugins.preFilter.IJSubtractBackground;
import processing.Filters;
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
    NumberParameter backgroundSubtractionRadius = new BoundedNumberParameter("Background Subtraction Radius", 0, 20, 0, null);
    NumberParameter minAngle = new BoundedNumberParameter("Minimal Angle for search", 2, -10, -90, 90);
    NumberParameter maxAngle = new BoundedNumberParameter("Maximal Angle for search", 2, 10, -90, 90);
    NumberParameter precision1 = new BoundedNumberParameter("Angular Precision of first seach", 2, 1, 0, null);
    NumberParameter precision2 = new BoundedNumberParameter("Angular Precision", 2, 0.1, 0, 1);
    //NumberParameter filterScale = new BoundedNumberParameter("Object Scale", 0, 15, 2, null); //TODO: conditional parameter
    ChoiceParameter interpolation = new ChoiceParameter("Interpolation", Utils.toStringArray(ImageTransformation.InterpolationScheme.values()), ImageTransformation.InterpolationScheme.BSPLINE5.toString(), false);
    ChoiceParameter searchMethod = new ChoiceParameter("Search method", SearchMethod.getValues(), SearchMethod.MAXVAR.getName(), false);
    
    Parameter[] parameters = new Parameter[]{minAngle, maxAngle, precision1, precision2, interpolation, backgroundSubtractionRadius};
    ArrayList<Double> internalParams=new ArrayList<Double>(1);

    public AutoRotationXY(double minAngle, double maxAngle, double precision1, double precision2, InterpolationScheme interpolation, SearchMethod method, int backgroundSubtractionRadius) {
        this.minAngle.setValue(minAngle);
        this.maxAngle.setValue(maxAngle);
        this.precision1.setValue(precision1);
        this.precision2.setValue(precision2);
        if (interpolation!=null) this.interpolation.setSelectedItem(interpolation.toString());
        this.searchMethod.setSelectedItem(method.getName());
        this.backgroundSubtractionRadius.setValue(backgroundSubtractionRadius);
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
        Image image = inputImages.getImage(channelIdx, inputImages.getDefaultTimePoint());
        double angle=0;
        if (this.searchMethod.getSelectedItem().equals(SearchMethod.MAXVAR.getName())) { 
            angle = computeRotationAngleXY(image, image.getSizeZ()/2, this.backgroundSubtractionRadius.getValue().intValue(), minAngle.getValue().doubleValue(), maxAngle.getValue().doubleValue(), precision1.getValue().doubleValue(), precision2.getValue().doubleValue(), true, false, 0);
        } else if (this.searchMethod.getSelectedItem().equals(SearchMethod.MAXARTEFACT.getName())) {
            angle = computeRotationAngleXY(image, image.getSizeZ()/2, this.backgroundSubtractionRadius.getValue().intValue(), minAngle.getValue().doubleValue(), maxAngle.getValue().doubleValue(), precision1.getValue().doubleValue(), precision2.getValue().doubleValue(), false, true, 0);
        }
        
        //ImageFloat sin = RadonProjection.getSinogram(image, minAngle.getValue().doubleValue()+90, maxAngle.getValue().doubleValue()+90, precision1.getValue().doubleValue(), Math.min(image.getSizeX(), image.getSizeY())); //(int)Math.sqrt(image.getSizeX()*image.getSizeX() + image.getSizeY()*image.getSizeY())
        //new IJImageDisplayer().showImage(sin);
        internalParams = new ArrayList<Double>(1);
        internalParams.add(angle);
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return ImageTransformation.rotateXY(TypeConverter.toFloat(image, null), internalParams.get(0), ImageTransformation.InterpolationScheme.valueOf(interpolation.getSelectedItem()));
    }
    
    public static double computeRotationAngleXY(Image image, int z, int backgroundSubtractionRadius, double ang1, double ang2, double stepsize, float[] proj, boolean var, double filterScale) {
        // subtract background
        if (backgroundSubtractionRadius>0) {
            image = image.duplicate(image.getName());
            IJSubtractBackground.filter(image, backgroundSubtractionRadius, true, false, true, false);
        }
        // initial search
        double[] angles = getAngleArray(ang1, ang2, stepsize);
        double angleMax=angles[0];
        double max=-1;
        //ImageFloat sinogram = new ImageFloat("sinogram", angles.length, proj.length, 1);
        for (int angleIdx = 0; angleIdx<angles.length; ++angleIdx) { // first search
            radonProject(image, z, angles[angleIdx]+90, proj);
            //paste(proj, sinogram, angleIdx);
            //if (filterScale>0) filter(filterScale, proj);
            double tempMax = var?RadonProjection.var(proj):RadonProjection.max(proj);
            if (tempMax > max) {
                max = tempMax;
                angleMax = angles[angleIdx];
            }
            //logger.trace("radon projection: computeRotationAngleXY: {}", angleMax);
        }
        //ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(sinogram);
        return -angleMax;
    }
    
    public static double computeRotationAngleXY(Image image, int z, int backgroundSubtractionRadius , double ang1, double ang2, double stepsize1, double stepsize2, boolean var, boolean rotate90, double filterScale) {
        // first search:
        //float[] proj = new float[(int)Math.sqrt(image.getSizeX()*image.getSizeX() + image.getSizeY()*image.getSizeY())];
        float[] proj = new float[Math.min(image.getSizeX(),image.getSizeY())];
        double inc = rotate90?90:0;
        double firstSearch = -computeRotationAngleXY(image, z, backgroundSubtractionRadius, ang1+inc, ang2+inc, stepsize1, proj, var, filterScale);
        double secondSearch = computeRotationAngleXY(image, z,backgroundSubtractionRadius, firstSearch-stepsize1+stepsize2, firstSearch+stepsize1-stepsize2, stepsize2, proj, var, filterScale);
        logger.debug("radon rotation search: first: {} second: {}", -firstSearch, secondSearch);
        return secondSearch+inc;
    }
    
    
    
    private static void filter(double scale, float[] data) {
        ImageFloat im = new ImageFloat("", data.length, new float[][]{data});
        im = Filters.median(im, im, new EllipsoidalNeighborhood(2, false));
        im = Filters.tophat(im, im, new EllipsoidalNeighborhood(scale, false));
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
