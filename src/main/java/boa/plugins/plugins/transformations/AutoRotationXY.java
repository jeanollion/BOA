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
package boa.plugins.plugins.transformations;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.FilterSequence;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PreFilterSequence;
import static boa.core.Processor.logger;
import boa.data_structure.input_image.InputImage;
import boa.data_structure.input_image.InputImages;
import static boa.data_structure.input_image.InputImages.getAverageFrame;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.List;
import boa.plugins.TransformationTimeIndependent;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import boa.image.processing.ImageTransformation;
import boa.image.processing.ImageTransformation.InterpolationScheme;
import boa.image.processing.RadonProjection;
import static boa.image.processing.RadonProjection.getAngleArray;
import static boa.image.processing.RadonProjection.radonProject;
import boa.image.processing.neighborhood.EllipsoidalNeighborhood;
import boa.utils.ArrayUtil;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class AutoRotationXY implements TransformationTimeIndependent {
    NumberParameter minAngle = new BoundedNumberParameter("Minimal Angle for search", 2, -10, -90, 90);
    NumberParameter maxAngle = new BoundedNumberParameter("Maximal Angle for search", 2, 10, -90, 90);
    NumberParameter precision1 = new BoundedNumberParameter("Angular Precision of first seach", 2, 1, 0, null);
    NumberParameter precision2 = new BoundedNumberParameter("Angular Precision", 2, 0.1, 0, 1);
    //NumberParameter filterScale = new BoundedNumberParameter("Object Scale", 0, 15, 2, null); //TODO: conditional parameter
    ChoiceParameter interpolation = new ChoiceParameter("Interpolation", Utils.toStringArray(ImageTransformation.InterpolationScheme.values()), ImageTransformation.InterpolationScheme.BSPLINE5.toString(), false); 
    ChoiceParameter searchMethod = new ChoiceParameter("Search method", SearchMethod.getValues(), SearchMethod.MAXVAR.getName(), false);
    NumberParameter frameNumber = new BoundedNumberParameter("Number of frame", 0, 10, 0, null);
    BooleanParameter removeIncompleteRowsAndColumns = new BooleanParameter("Remove Incomplete rows and columns", true);
    FilterSequence prefilters = new FilterSequence("Pre-Filters");
    BooleanParameter maintainMaximum = new BooleanParameter("Maintain Maximum Value", true).setToolTipText("In case of saturated value & interpolation with polynomes of degree>1, higher values than maximal value can be created, which can be an issue in case of a saturated image. This option will saturate the rotated image to the old maximal value");
    Parameter[] parameters = new Parameter[]{searchMethod, minAngle, maxAngle, precision1, precision2, interpolation, frameNumber, removeIncompleteRowsAndColumns, maintainMaximum, prefilters}; //  
    ArrayList<Double> internalParams=new ArrayList<Double>(1);
    public boolean testMode = false;
    public AutoRotationXY(double minAngle, double maxAngle, double precision1, double precision2, InterpolationScheme interpolation, SearchMethod method) {
        this.minAngle.setValue(minAngle);
        this.maxAngle.setValue(maxAngle);
        this.precision1.setValue(precision1);
        this.precision2.setValue(precision2);
        if (interpolation!=null) this.interpolation.setSelectedItem(interpolation.toString());
        this.searchMethod.setSelectedItem(method.getName());
        //this.backgroundSubtractionRadius.setValue(backgroundSubtractionRadius);
    }
    public AutoRotationXY setPrefilters(boa.plugins.Filter... filters) {
        prefilters.add(filters);
        return this;
    }
    public AutoRotationXY setFrameNumber(int frameNumber) {
        this.frameNumber.setValue(frameNumber);
        return this;
    }
    public AutoRotationXY setRemoveIncompleteRowsAndColumns(boolean remove) {
        this.removeIncompleteRowsAndColumns.setSelected(remove);
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
    public double getAngle(Image image) {
        double angle=0;
        if (this.searchMethod.getSelectedItem().equals(SearchMethod.MAXVAR.getName())) { 
            angle = computeRotationAngleXY(image, image.sizeZ()/2, minAngle.getValue().doubleValue(), maxAngle.getValue().doubleValue(), precision1.getValue().doubleValue(), precision2.getValue().doubleValue(), true, false, 0);
        } else if (this.searchMethod.getSelectedItem().equals(SearchMethod.MAXARTEFACT.getName())) {
            angle = computeRotationAngleXY(image, image.sizeZ()/2, minAngle.getValue().doubleValue(), maxAngle.getValue().doubleValue(), precision1.getValue().doubleValue(), precision2.getValue().doubleValue(), false, true, 0);
        }
        return angle;
    }
    public Image rotate(Image image) {
        return ImageTransformation.rotateXY(TypeConverter.toFloat(image, null), getAngle(image), ImageTransformation.InterpolationScheme.valueOf(interpolation.getSelectedItem()), removeIncompleteRowsAndColumns.getSelected());
    }
    List<Image> sinogram1Test, sinogram2Test;
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {     
        if (testMode) {
            sinogram1Test = new ArrayList<>();
            sinogram2Test = new ArrayList<>();
        }
        // TODO search for best image to Rotate ... better dispertion of signal ? using spatial moments? average     on several frames ?
        int fn = Math.min(frameNumber.getValue().intValue(), inputImages.getFrameNumber());
        List<Integer> frames;
        if (fn<=1) frames = new ArrayList<Integer>(1){{add(inputImages.getDefaultTimePoint());}};
        else frames = InputImages.chooseNImagesWithSignal(inputImages, channelIdx, fn);
        
        List<Double> angles = new ArrayList<>(fn);
        for (int f : frames) {
            Image<? extends Image> image = inputImages.getImage(channelIdx, f);
            image = prefilters.filter(image);
            if (image.sizeZ()>1) {
                int plane = inputImages.getBestFocusPlane(f);
                if (plane<0) throw new RuntimeException("Autorotation can only be run on 2D images AND no autofocus algorithm was set");
                image = image.splitZPlanes().get(plane);
            }
            double angle=getAngle(image);
            angles.add(angle);
        }
        if (testMode) {
            ImageWindowManagerFactory.showImage(Image.mergeZPlanes(sinogram1Test).setName("Sinogram: first search"));
            ImageWindowManagerFactory.showImage(Image.mergeZPlanes(sinogram2Test).setName("Sinogram: second search"));
            sinogram1Test.clear();
            sinogram2Test.clear();
        }
        double medianAngle = ArrayUtil.median(angles);
        logger.debug("autorotation: median angle: {} among: {}", medianAngle, Utils.toStringList(Utils.toList(ArrayUtil.generateIntegerArray(fn)), i->"f:"+frames.get(i)+"->"+angles.get(i)));
        internalParams = new ArrayList<Double>(1);
        internalParams.add(medianAngle);
    }
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        if (internalParams==null || internalParams.isEmpty()) throw new RuntimeException("Autorotation not configured");
        Image res = ImageTransformation.rotateXY(TypeConverter.toFloat(image, null), internalParams.get(0), ImageTransformation.InterpolationScheme.valueOf(interpolation.getSelectedItem()), removeIncompleteRowsAndColumns.getSelected());
        if (maintainMaximum.getSelected() && interpolation.getSelectedIndex()>1) {
            double oldMax = image.getMinAndMax(null)[1];
            SaturateHistogram.saturate(oldMax, oldMax, res);
        }
        return res;
    }
    
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber) {
        return internalParams!=null && !internalParams.isEmpty();
    }
    
    public double[] computeRotationAngleXY(Image image, int z, double ang1, double ang2, double stepsize, float[] proj, boolean var, double filterScale) {

        // initial search
        double[] angles = getAngleArray(ang1, ang2, stepsize);
        double[] angleMax=new double[]{angles[0], angles[0]};
        double max=-1;
        ImageFloat sinogram = null;
        if (testMode) sinogram = new ImageFloat("sinogram search angles: ["+ang1+";"+ang2+"]", angles.length, proj.length, 1);
        for (int angleIdx = 0; angleIdx<angles.length; ++angleIdx) { // first search
            radonProject(image, z, angles[angleIdx]+90, proj);
            if (testMode) paste(proj, sinogram, angleIdx);
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
        if (testMode) {
            if (sinogram1Test.size()<=sinogram2Test.size()) sinogram1Test.add(sinogram);
            else sinogram2Test.add(sinogram);
        }
        angleMax[0] = - angleMax[0];
        angleMax[1] = - angleMax[1];
        return angleMax;
    }
    
    public double computeRotationAngleXY(Image image, int z , double ang1, double ang2, double stepsize1, double stepsize2, boolean var, boolean rotate90, double filterScale) {
        // first search:
        //float[] proj = new float[(int)Math.sqrt(image.getSizeX()*image.getSizeX() + image.getSizeY()*image.getSizeY())];
        float[] proj = new float[Math.min(image.sizeX(),image.sizeY())];
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
        im = boa.image.processing.Filters.median(im, im, new EllipsoidalNeighborhood(2, false));
        im = boa.image.processing.Filters.tophat(im, im, new EllipsoidalNeighborhood(scale, false));
        float[] data2 = im.getPixelArray()[0];
        for (int i = 0; i<data.length; ++i) data[i] = data2[i];
    }
    
    public static enum SearchMethod {
        MAXVAR("Fluo Microchannel"), // cherche le maximum variance. necessite de supprimer le bruit de fond
        MAXARTEFACT("Phase Microchannel Artifact"); // se base sur l'artecfact d'imagerie, cherche la valeur max qui est a +90. 
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
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
