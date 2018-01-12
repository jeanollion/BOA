/*
 * Copyright (C) 2016 jollion
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
package plugins.plugins.measurements.objectFeatures;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import configuration.parameters.SiblingStructureParameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.Region;
import dataStructure.objects.RegionPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.ImageByte;
import image.ImageInteger;
import image.ImageMask;
import image.TypeConverter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import plugins.objectFeature.IntensityMeasurement;
import plugins.objectFeature.IntensityMeasurementCore.IntensityMeasurements;
import processing.Filters;
import utils.HashMapGetCreate;
import utils.Pair;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SNR extends IntensityMeasurement {
    protected SiblingStructureParameter backgroundObject = new SiblingStructureParameter("Background Object", true).setAutoConfiguration(true);
    protected BoundedNumberParameter dilateExcluded = new BoundedNumberParameter("Radius for excluded structure dillatation", 1, 1, 0, null);
    protected BoundedNumberParameter erodeBorders = new BoundedNumberParameter("Radius for border erosion", 1, 1, 0, null);
    protected ChoiceParameter formula = new ChoiceParameter("Formula", new String[]{"(F-B)/sd(B)", "F-B"}, "(F-B)/sd(B)", false);
    protected ChoiceParameter foregroundFormula = new ChoiceParameter("Foreground", new String[]{"mean", "max", "value at center"}, "mean", false);
    @Override public Parameter[] getParameters() {return new Parameter[]{intensity, backgroundObject, formula, foregroundFormula, dilateExcluded, erodeBorders};}
    HashMap<Region, Region> childrenParentMap;
    BoundingBox childrenOffset;
    BoundingBox parentOffsetRev;
    public SNR() {}
    public SNR(int backgroundStructureIdx) {
        backgroundObject.setSelectedStructureIdx(backgroundStructureIdx);
    }
    public SNR setBackgroundObjectStructureIdx(int structureIdx) {
        backgroundObject.setSelectedStructureIdx(structureIdx);
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
    @Override public IntensityMeasurement setUp(StructureObject parent, int childStructureIdx, RegionPopulation childPopulation) {
        super.setUp(parent, childStructureIdx, childPopulation);
        if (childPopulation.getObjects().isEmpty()) return this;
        if (!childPopulation.isAbsoluteLandmark()) childrenOffset = parent.getBounds(); // the step it still at processing, thus their offset of objects is related to their direct parent
        else childrenOffset = new BoundingBox(0, 0, 0); // absolute offsets
        parentOffsetRev = parent.getBounds().duplicate().reverseOffset();
        
        // get parents
        List<Region> parents;
        if (backgroundObject.getSelectedStructureIdx()!=super.parent.getStructureIdx()) {
            parents = parent.getObjectPopulation(backgroundObject.getSelectedStructureIdx()).getObjects();
        } else {
            parents = new ArrayList<Region>(1);
            parents.add(parent.getObject());
        }
        double erodeRad= this.erodeBorders.getValue().doubleValue();
        double dilRad = this.dilateExcluded.getValue().doubleValue();
        // assign parents to children by inclusion
        HashMapGetCreate<Region, List<Pair<Region, Region>>> parentChildrenMap = new HashMapGetCreate<>(parents.size(), new HashMapGetCreate.ListFactory());
        for (Region o : childPopulation.getObjects()) {
            Region p = StructureObjectUtils.getInclusionParent(o, parents, childrenOffset, null);
            if (p!=null) {
                Region oDil = o;
                if (dilRad>0)  {
                    ImageInteger oMask = o.getMask();
                    oMask = Filters.binaryMax(oMask, null, Filters.getNeighborhood(dilRad, dilRad, oMask), false, true);
                    oDil = new Region(oMask, 1, o.is2D());
                }
                parentChildrenMap.getAndCreateIfNecessary(p).add(new Pair(o, oDil));
            }
        }
        
        // remove foreground objects from background mask & erodeit
        childrenParentMap = new HashMap<Region, Region>();
        for (Region p : parents) {
            ImageMask ref = p.getMask();
            List<Pair<Region, Region>> children = parentChildrenMap.get(p);
            if (children!=null) {
                ImageByte mask  = TypeConverter.toByteMask(ref, null, 1).setName("SNR mask");
                for (Pair<Region, Region> o : parentChildrenMap.get(p)) o.value.draw(mask, 0, childrenOffset);
                /*if (backgroundObject.getSelectedStructureIdx()==super.parent.getStructureIdx()) {
                    for (Region o : parentChildrenMap.get(p)) o.draw(mask, 0);
                } else {
                    for (Region o : parentChildrenMap.get(p)) o.draw(mask, 0, childrenOffset);
                }*/
                if (erodeRad>0) {
                    ImageByte maskErode = Filters.binaryMin(mask, null, Filters.getNeighborhood(erodeRad, erodeRad, mask), true); // erode mask // TODO dillate objects?
                    if (maskErode.count()>0) mask = maskErode;
                }
                Region parentObject = new Region(mask, 1, p.is2D());
                for (Pair<Region, Region> o : children) childrenParentMap.put(o.key, parentObject);
                
                //ImageWindowManagerFactory.showImage( mask);
            }
            
        }
        return this;
    }
    @Override
    public double performMeasurement(Region object, BoundingBox offset) {
        
        if (core==null) synchronized(this) {setUpOrAddCore(null, null);}
        Region parentObject; 
        if (childrenParentMap==null) parentObject = super.parent.getObject();
        else parentObject=this.childrenParentMap.get(object);
        if (parentObject==null) return 0;
        IntensityMeasurements iParent = super.core.getIntensityMeasurements(parentObject, null);
        IntensityMeasurements fore = super.core.getIntensityMeasurements(object, offset);
        //logger.debug("SNR: object: {}, value: {}, fore:{}, back I: {} back SD: {}", object.getLabel(), (fore-iParent.mean ) / iParent.sd, fore, iParent.mean, iParent.sd);
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

    public String getDefaultName() {
        return "snr";
    }
    
}
