/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.measurements;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.image.Image;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.processing.ImageOperations;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.List;
import boa.measurement.BasicMeasurements;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;
import boa.plugins.plugins.measurements.objectFeatures.SNR;
import boa.image.processing.Filters;

/**
 *
 * @author jollion
 */
public class SimpleIntensityMeasurementStructureExclusion implements Measurement {
    protected StructureParameter structureObject = new StructureParameter("Object", -1, false, false);
    protected StructureParameter excludedStructure = new StructureParameter("Excluded Structure", -1, false, false);
    protected StructureParameter structureImage = new StructureParameter("Image", -1, false, false);
    protected BoundedNumberParameter dilateExcluded = new BoundedNumberParameter("Radius for excluded structure dillatation", 1, 2, 0, null);
    protected BoundedNumberParameter erodeBorders = new BoundedNumberParameter("Radius for border erosion", 1, 2, 0, null);
    protected BooleanParameter addMeasurementToExcludedStructure = new BooleanParameter("set Measurement to excluded structure", false);
    TextParameter prefix = new TextParameter("Prefix", "Intensity", false);
    protected Parameter[] parameters = new Parameter[]{structureObject, structureImage, excludedStructure, dilateExcluded, erodeBorders, prefix, addMeasurementToExcludedStructure};
    
    public SimpleIntensityMeasurementStructureExclusion() {}
    
    public SimpleIntensityMeasurementStructureExclusion(int object, int image, int exclude) {
        this.structureObject.setSelectedStructureIdx(object);
        this.structureImage.setSelectedStructureIdx(image);
        this.excludedStructure.setSelectedStructureIdx(exclude);
    }
    
    public SimpleIntensityMeasurementStructureExclusion setRadii(double dilateExclude, double erodeMainObject) {
        this.dilateExcluded.setValue(dilateExclude);
        this.erodeBorders.setValue(erodeMainObject);
        return this;
    } 
    
    public SimpleIntensityMeasurementStructureExclusion setPrefix(String prefix) {
        this.prefix.setValue(prefix);
        return this;
    } 
    
    @Override public int getCallStructure() {
        return structureObject.getSelectedStructureIdx();
    }

    @Override public boolean callOnlyOnTrackHeads() {
        return false;
    }

    @Override public List<MeasurementKey> getMeasurementKeys() {
        int structureIdx = addMeasurementToExcludedStructure.getSelected() ? excludedStructure.getSelectedStructureIdx() : structureObject.getSelectedStructureIdx();
        ArrayList<MeasurementKey> res = new ArrayList<>();
        res.add(new MeasurementKeyObject(prefix.getValue()+"Mean", structureIdx));
        res.add(new MeasurementKeyObject(prefix.getValue()+"Sigma", structureIdx));
        res.add(new MeasurementKeyObject(prefix.getValue()+"PixCount", structureIdx));
        return res;
    }

    @Override public void performMeasurement(StructureObject object) {
        StructureObject parent = object.isRoot() ? object : object.getParent();
        Image image = parent.getRawImage(structureImage.getSelectedStructureIdx());
        double[] meanSd = ImageOperations.getMeanAndSigmaWithOffset(image, getMask(object, excludedStructure.getSelectedStructureIdx(), dilateExcluded.getValue().doubleValue(), erodeBorders.getValue().doubleValue()), null);
        object.getMeasurements().setValue(prefix.getValue()+"Mean", meanSd[0]);
        object.getMeasurements().setValue(prefix.getValue()+"Sigma", meanSd[1]);
        object.getMeasurements().setValue(prefix.getValue()+"PixCount", meanSd[2]);
    }
    
    public static ImageByte getMask(StructureObject parent, int excludeStructureIdx, double dilateExcludeRadius, double erodeObjectRaduis) {
        List<StructureObject> children = parent.getChildren(excludeStructureIdx);
        ImageByte mask  = TypeConverter.toByteMask(parent.getMask(), null, 1).setName("mask:");
        if (erodeObjectRaduis>0) {
            ImageByte maskErode = Filters.binaryMin(mask, null, Filters.getNeighborhood(erodeObjectRaduis, mask), true);
            //if (maskErode.count()>0) mask = maskErode;
            mask = maskErode;
        }
        for (StructureObject o : children) {
            Region ob = o.getObject();
            if (dilateExcludeRadius>0)  {
                ImageInteger oMask = o.getObject().getMaskAsImageInteger();
                oMask = Filters.binaryMax(oMask, null, Filters.getNeighborhood(dilateExcludeRadius, oMask), false, true);
                ob = new Region(oMask, 1, ob.is2D());
            }
            ob.draw(mask, 0, null);
        }
        //ImageWindowManagerFactory.showImage(mask);
        return mask;
    }

    @Override public Parameter[] getParameters() {
        return parameters;
    }
    
}
