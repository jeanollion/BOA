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
import configuration.parameters.Parameter;
import configuration.parameters.SiblingStructureParameter;
import configuration.parameters.StructureParameter;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectPopulation;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import image.BoundingBox;
import image.BoundingBox.LoopFunction;
import image.ImageByte;
import image.ImageMask;
import image.TypeConverter;
import java.util.ArrayList;
import java.util.HashMap;
import plugins.objectFeature.IntensityMeasurement;
import plugins.objectFeature.IntensityMeasurementCore.IntensityMeasurements;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SNR extends IntensityMeasurement {
    protected SiblingStructureParameter backgroundObject = new SiblingStructureParameter("Background Object", true).setAutoConfiguration(true);
    
    @Override public Parameter[] getParameters() {return new Parameter[]{intensity, backgroundObject};}
    ArrayList<Object3D> parents;
    ArrayList<Object3D> parentMasks;
    HashMap<Object3D, Object3D> childrenParentMap;
    BoundingBox childrenOffset;
    BoundingBox parentOffsetRev;
    public SNR() {}
    
    public SNR setBackgroundObjectStructureIdx(int structureIdx) {
        backgroundObject.setSelectedStructureIdx(structureIdx);
        return this;
    }
    @Override public IntensityMeasurement setUp(StructureObject parent, int childStructureIdx, ObjectPopulation childPopulation) {
        super.setUp(parent, childStructureIdx, childPopulation);
        if (childPopulation.getObjects().isEmpty()) return this;
        if (!childPopulation.isAbsoluteLandmark()) childrenOffset = parent.getBounds(); // the step it still at processing, thus their offset of objects is related to their direct parent
        else childrenOffset = new BoundingBox(0, 0, 0);
        parentOffsetRev = parent.getBounds().duplicate().reverseOffset();
        if (backgroundObject.getSelectedStructureIdx()!=super.parent.getStructureIdx()) {
            parents = parent.getObjectPopulation(backgroundObject.getSelectedStructureIdx()).getObjects();
        } else {
            parents = new ArrayList<Object3D>(1);
            parents.add(parent.getObject());
        }
        childrenParentMap = new HashMap<Object3D, Object3D>();
        for (Object3D o : childPopulation.getObjects()) {
            Object3D p = StructureObjectUtils.getInclusionParent(o, parents, childrenOffset, null);
            if (p!=null) childrenParentMap.put(o, p);
        }
        parentMasks = new ArrayList<Object3D>(parents.size());
        for (Object3D p : parents) {
            final ImageMask ref = p.getMask();
            final ImageByte mask  = TypeConverter.toByteMask(ref, null).setName("mask:");
            parentMasks.add(new Object3D(mask, 1));
            if (backgroundObject.getSelectedStructureIdx()==super.parent.getStructureIdx()) {
                for (Object3D o : childPopulation.getObjects()) {
                    o.draw(mask, 0);
                    // dilate
                }
            }
            else {
                for (Object3D o : Utils.getKeys(childrenParentMap, p)) {
                    o.draw(mask, 0, childrenOffset);
                    //dilate
                }
            }
            //new IJImageDisplayer().showImage(mask);
        }
        return this;
    }
    public double performMeasurement(Object3D object, BoundingBox offset) {
        if (core==null) synchronized(this) {setUpOrAddCore(null);}
        Object3D parentObject; 
        if (parents==null) parentObject = super.parent.getObject();
        else parentObject=this.childrenParentMap.get(object);
        if (parentObject==null) return 0;
        IntensityMeasurements iParent = super.core.getIntensityMeasurements(parentObject, null);
        double fore = super.core.getIntensityMeasurements(object, offset).mean;
        logger.debug("SNR: fore:{}, back I: {} back SD: {}", fore, iParent.mean, iParent.sd);
        return ( fore-iParent.mean ) / iParent.sd;
    }

    public String getDefaultName() {
        return "snr";
    }
    
}
