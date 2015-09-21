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

import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import static core.Processor.logger;
import dataStructure.containers.InputImage;
import dataStructure.containers.InputImages;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;
import plugins.TransformationTimeIndependent;
import processing.ImageTransformation;
import processing.ImageTransformation.InterpolationScheme;
import processing.RadonProjection;
import static processing.RadonProjection.getAngleArray;
import static processing.RadonProjection.radonProject;

/**
 *
 * @author jollion
 */
public class AutoRotationXY implements TransformationTimeIndependent {
    NumberParameter minAngle = new NumberParameter("Minimal Angle for search", 2, -10);
    NumberParameter maxAngle = new NumberParameter("Maximal Angle for search", 2, 10);
    NumberParameter precision1 = new NumberParameter("Angular Precision of first seach", 2, 1);
    NumberParameter precision2 = new NumberParameter("Angular Precision", 0, 0.1);
    ChoiceParameter interpolation = new ChoiceParameter("Interpolation", InterpolationScheme.getValues(), ImageTransformation.InterpolationScheme.BSPLINE5.toString(), false);
    ChoiceParameter searchMethod = new ChoiceParameter("Search method", SearchMethod.getValues(), SearchMethod.MAXVAR.getName(), false);
    Parameter[] parameters = new Parameter[]{minAngle, maxAngle, precision1, precision2, interpolation};
    Double[] internalParams;

    public AutoRotationXY(double minAngle, double maxAngle, double precision1, double precision2, InterpolationScheme interpolation, SearchMethod method) {
        this.minAngle.setValue(minAngle);
        this.maxAngle.setValue(maxAngle);
        this.precision1.setValue(precision1);
        this.precision2.setValue(precision2);
        if (interpolation!=null) this.interpolation.setSelectedItem(interpolation.toString());
        this.searchMethod.setSelectedItem(method.getName());
    }
    
    public AutoRotationXY() {}
    
    public boolean isTimeDependent() {
        return false;
    }

    public Object[] getConfigurationData() {
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
            angle = computeRotationAngleXY(image, image.getSizeZ()/2, minAngle.getValue().doubleValue(), maxAngle.getValue().doubleValue(), precision1.getValue().doubleValue(), precision2.getValue().doubleValue(), true, false);
        } else if (this.searchMethod.getSelectedItem().equals(SearchMethod.MAXARTEFACT.getName())) {
            angle = computeRotationAngleXY(image, image.getSizeZ()/2, minAngle.getValue().doubleValue(), maxAngle.getValue().doubleValue(), precision1.getValue().doubleValue(), precision2.getValue().doubleValue(), false, true);
        }
        internalParams = new Double[]{angle};
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        return ImageTransformation.rotateXY(image, internalParams[0], ImageTransformation.InterpolationScheme.valueOf(interpolation.getSelectedItem()));
    }
    
    public static double computeRotationAngleXY(Image image, int z, double ang1, double ang2, double stepsize, float[] proj, boolean var) {
        // initial search
        double[] angles = getAngleArray(ang1, ang2, stepsize);
        double angleMax=angles[0];
        double max=-1;
        //ImageFloat sinogram = new ImageFloat("sinogram", angles.length, proj.length, 1);
        for (int angleIdx = 1; angleIdx<angles.length; ++angleIdx) { // first search
            radonProject(image, z, angles[angleIdx], proj);
            //paste(proj, sinogram, angleIdx);
            double tempMax = var?var(proj):max(proj);
            if (tempMax > max) {
                max = tempMax;
                angleMax = angles[angleIdx];
            }
            //logger.trace("radon projection: computeRotationAngleXY: {}", angleMax);
        }
        //ImageWindowManagerFactory.getImageManager().getDisplayer().showImage(sinogram);
        return -angleMax;
    }
    
    public static double computeRotationAngleXY(Image image, int z , double ang1, double ang2, double stepsize1, double stepsize2, boolean var, boolean rotate90) {
        // first search:
        float[] proj = new float[image.getSizeX()];
        double inc = rotate90?90:0;
        double firstSearch = -computeRotationAngleXY(image, z, ang1+inc, ang2+inc, stepsize1, proj, var);
        double secondSearch = computeRotationAngleXY(image, z, firstSearch-stepsize1+stepsize2, firstSearch+stepsize1-stepsize2, stepsize2, proj, var);
        logger.debug("radon rotation search: first: {} second: {}", -firstSearch, secondSearch);
        return secondSearch+inc;
    }
    
    private static float max(float[] array) {
        float max = array[0];
        for (int i = 1; i<array.length; ++i) {
            if (array[i]>max) max = array[i];
        }
        return max;
    }
    
    private static double var(float[] array) {
        if (array.length==0) return 0;
        double sum = 0, sum2=0;
        for (float f : array){sum+=f; sum2+=f*f;}
        sum/=(float)array.length;
        sum2/=(float)array.length;
        return sum2-sum*sum;
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
