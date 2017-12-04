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
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
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
    HashMap<Object3D, Object3D> childrenParentMap;
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
    @Override public IntensityMeasurement setUp(StructureObject parent, int childStructureIdx, ObjectPopulation childPopulation) {
        super.setUp(parent, childStructureIdx, childPopulation);
        if (childPopulation.getObjects().isEmpty()) return this;
        if (!childPopulation.isAbsoluteLandmark()) childrenOffset = parent.getBounds(); // the step it still at processing, thus their offset of objects is related to their direct parent
        else childrenOffset = new BoundingBox(0, 0, 0); // absolute offsets
        parentOffsetRev = parent.getBounds().duplicate().reverseOffset();
        
        // get parents
        List<Object3D> parents;
        if (backgroundObject.getSelectedStructureIdx()!=super.parent.getStructureIdx()) {
            parents = parent.getObjectPopulation(backgroundObject.getSelectedStructureIdx()).getObjects();
        } else {
            parents = new ArrayList<Object3D>(1);
            parents.add(parent.getObject());
        }
        double erodeRad= this.erodeBorders.getValue().doubleValue();
        double dilRad = this.dilateExcluded.getValue().doubleValue();
        // assign parents to children by inclusion
        HashMapGetCreate<Object3D, List<Pair<Object3D, Object3D>>> parentChildrenMap = new HashMapGetCreate<>(parents.size(), new HashMapGetCreate.ListFactory());
        for (Object3D o : childPopulation.getObjects()) {
            Object3D p = StructureObjectUtils.getInclusionParent(o, parents, childrenOffset, null);
            if (p!=null) {
                Object3D oDil = o;
                if (dilRad>0)  {
                    ImageInteger oMask = o.getMask();
                    oMask = Filters.binaryMax(oMask, null, Filters.getNeighborhood(dilRad, dilRad, oMask), false, true);
                    oDil = new Object3D(oMask, 1);
                }
                parentChildrenMap.getAndCreateIfNecessary(p).add(new Pair(o, oDil));
            }
        }
        
        // remove foreground objects from background mask & erodeit
        childrenParentMap = new HashMap<Object3D, Object3D>();
        for (Object3D p : parents) {
            ImageMask ref = p.getMask();
            List<Pair<Object3D, Object3D>> children = parentChildrenMap.get(p);
            if (children!=null) {
                ImageByte mask  = TypeConverter.toByteMask(ref, null, 1).setName("SNR mask");
                for (Pair<Object3D, Object3D> o : parentChildrenMap.get(p)) o.value.draw(mask, 0, childrenOffset);
                /*if (backgroundObject.getSelectedStructureIdx()==super.parent.getStructureIdx()) {
                    for (Object3D o : parentChildrenMap.get(p)) o.draw(mask, 0);
                } else {
                    for (Object3D o : parentChildrenMap.get(p)) o.draw(mask, 0, childrenOffset);
                }*/
                if (erodeRad>0) {
                    ImageByte maskErode = Filters.binaryMin(mask, null, Filters.getNeighborhood(erodeRad, erodeRad, mask), true); // erode mask // TODO dillate objects?
                    if (maskErode.count()>0) mask = maskErode;
                }
                Object3D parentObject = new Object3D(mask, 1);
                for (Pair<Object3D, Object3D> o : children) childrenParentMap.put(o.key, parentObject);
                
                //ImageWindowManagerFactory.showImage( mask);
            }
            
        }
        return this;
    }
    @Override
    public double performMeasurement(Object3D object, BoundingBox offset) {
        
        if (core==null) synchronized(this) {setUpOrAddCore(null, null);}
        Object3D parentObject; 
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
