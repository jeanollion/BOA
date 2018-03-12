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
package boa.plugins.plugins.measurements.objectFeatures;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.SiblingStructureParameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.Region;
import boa.data_structure.RegionPopulation;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import boa.image.BoundingBox;
import boa.image.ImageByte;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import boa.plugins.objectFeature.IntensityMeasurement;
import boa.plugins.objectFeature.IntensityMeasurementCore.IntensityMeasurements;
import boa.image.processing.Filters;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class SNR extends IntensityMeasurement {
    protected StructureParameter backgroundStructure = new StructureParameter("Background Structure");//.setAutoConfiguration(true);
    protected BoundedNumberParameter dilateExcluded = new BoundedNumberParameter("Dilatation radius for foreground object", 1, 1, 0, null).setToolTipText("Dilated foreground object will be excluded from background mask");
    protected BoundedNumberParameter erodeBorders = new BoundedNumberParameter("Radius for background mask erosion", 1, 1, 0, null).setToolTipText("Background mask will be erored in order to avoid border effects");
    protected ChoiceParameter formula = new ChoiceParameter("Formula", new String[]{"(F-B)/sd(B)", "F-B"}, "(F-B)/sd(B)", false);
    protected ChoiceParameter foregroundFormula = new ChoiceParameter("Foreground", new String[]{"mean", "max", "value at center"}, "mean", false);
    @Override public Parameter[] getParameters() {return new Parameter[]{intensity, backgroundStructure, formula, foregroundFormula, dilateExcluded, erodeBorders};}
    HashMap<Region, Region> foregroundMapBackground;
    BoundingBox foregorundOffset;
    BoundingBox parentOffsetRev;
    public SNR() {}
    public SNR(int backgroundStructureIdx) {
        backgroundStructure.setSelectedStructureIdx(backgroundStructureIdx);
    }
    public SNR setBackgroundObjectStructureIdx(int structureIdx) {
        backgroundStructure.setSelectedStructureIdx(structureIdx);
        return this;
    }
    public SNR setRadii(double dilateRadius, double erodeRadius) {
        this.dilateExcluded.setValue(dilateRadius);
        this.erodeBorders.setValue(erodeRadius);
        return this;
    }
    public SNR setFormula(int formula, int foreground) {
        this.formula.setSelectedIndex(formula);
        this.foregroundFormula.setSelectedIndex(foreground);
        return this;
    }
    @Override public IntensityMeasurement setUp(StructureObject parent, int childStructureIdx, RegionPopulation foregroundPopulation) {
        super.setUp(parent, childStructureIdx, foregroundPopulation);
        if (foregroundPopulation.getRegions().isEmpty()) return this;
        if (!foregroundPopulation.isAbsoluteLandmark()) foregorundOffset = parent.getBounds(); // the step it still at processing, thus their offset of objects is related to their direct parent
        else foregorundOffset = new BoundingBox(0, 0, 0); // absolute offsets
        parentOffsetRev = parent.getBounds().duplicate().reverseOffset();
        
        List<Region> backgroundObjects;
        if (backgroundStructure.getSelectedStructureIdx()!=super.parent.getStructureIdx()) {
            backgroundObjects = parent.getObjectPopulation(backgroundStructure.getSelectedStructureIdx()).getRegions();
        } else {
            backgroundObjects = new ArrayList<>(1);
            backgroundObjects.add(parent.getObject());
        }
        double erodeRad= this.erodeBorders.getValue().doubleValue();
        double dilRad = this.dilateExcluded.getValue().doubleValue();
        // assign parents to children by inclusion
        HashMapGetCreate<Region, List<Pair<Region, Region>>> backgroundMapForeground = new HashMapGetCreate<>(backgroundObjects.size(), new HashMapGetCreate.ListFactory());
        for (Region o : foregroundPopulation.getRegions()) {
            Region p = o.getContainer(backgroundObjects, foregorundOffset, null); // parents are in absolute offset
            if (p!=null) {
                Region oDil = o;
                if (dilRad>0)  {
                    ImageInteger oMask = o.getMaskAsImageInteger();
                    oMask = Filters.binaryMax(oMask, null, Filters.getNeighborhood(dilRad, dilRad, oMask), false, true);
                    oDil = new Region(oMask, 1, o.is2D()).setIsAbsoluteLandmark(o.isAbsoluteLandMark());
                }
                backgroundMapForeground.getAndCreateIfNecessary(p).add(new Pair(o, oDil));
            }
        }
        
        // remove foreground objects from background mask & erodeit
        foregroundMapBackground = new HashMap<>();
        for (Region backgroundRegion : backgroundObjects) {
            ImageMask ref = backgroundRegion.getMask();
            List<Pair<Region, Region>> children = backgroundMapForeground.get(backgroundRegion);
            if (children!=null) {
                ImageByte mask  = TypeConverter.toByteMask(ref, null, 1).setName("SNR mask");
                for (Pair<Region, Region> o : backgroundMapForeground.get(backgroundRegion)) o.value.draw(mask, 0, foregorundOffset);// was with offset: absolute = 0 / relative = parent
                if (erodeRad>0) {
                    ImageByte maskErode = Filters.binaryMin(mask, null, Filters.getNeighborhood(erodeRad, erodeRad, mask), true); // erode mask // TODO dilate objects?
                    if (maskErode.count()>0) mask = maskErode;
                }
                Region modifiedBackgroundRegion = new Region(mask, 1, backgroundRegion.is2D()).setIsAbsoluteLandmark(true);
                for (Pair<Region, Region> o : children) foregroundMapBackground.put(o.key, modifiedBackgroundRegion);
                
                //ImageWindowManagerFactory.showImage( mask);
            }
        }
        //logger.debug("init SNR: (s: {}/b:{}) foreground with back: {}/{}", intensity.getSelectedStructureIdx(), this.backgroundStructure.getSelectedStructureIdx(), foregroundMapBackground.size(), foregroundPopulation.getRegions().size());
        return this;
    }
    @Override
    public double performMeasurement(Region object) {
        
        if (core==null) synchronized(this) {setUpOrAddCore(null, null);}
        Region parentObject; 
        if (foregroundMapBackground==null) parentObject = super.parent.getObject();
        else parentObject=this.foregroundMapBackground.get(object);
        if (parentObject==null) return 0;
        IntensityMeasurements iParent = super.core.getIntensityMeasurements(parentObject);
        IntensityMeasurements fore = super.core.getIntensityMeasurements(object);
        //logger.debug("SNR: parent: {} object: {}, value: {}, fore:{}, back I: {} back SD: {}", super.parent, object.getLabel(), getValue(getForeValue(fore), iParent.mean, iParent.sd), getForeValue(fore), iParent.mean, iParent.sd);
        return getValue(getForeValue(fore), iParent.mean, iParent.sd);
    }
    
    protected double getForeValue(IntensityMeasurements fore) {
        switch (foregroundFormula.getSelectedIndex()) {
            case 0: return fore.mean;
            case 1: return fore.max;
            case 2: return fore.getValueAtCenter();
            default: return fore.mean;
        }     
    }
    
    protected double getValue(double fore, double back, double backSd) {
        if (this.formula.getSelectedIndex()==0) return (fore-back)/backSd;
        else return fore-back;
    }

    @Override public String getDefaultName() {
        return "SNR";
    }
    
}
