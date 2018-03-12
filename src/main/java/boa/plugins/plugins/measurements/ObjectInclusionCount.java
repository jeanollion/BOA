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

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.StructureParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.image.BoundingBox;
import boa.image.ImageMask;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.Measurement;

/**
 *
 * @author jollion
 */
public class ObjectInclusionCount implements Measurement {
    protected StructureParameter structureContainer = new StructureParameter("Containing Structure", -1, false, false);
    protected StructureParameter structureToCount = new StructureParameter("Structure to count", -1, false, false);
    protected BooleanParameter onlyTrackHeads = new BooleanParameter("Count Only TrackHeads", false);
    protected BoundedNumberParameter percentageInclusion = new BoundedNumberParameter("Minimum percentage of inclusion", 0, 100, 0, 100);
    protected TextParameter inclusionText = new TextParameter("Inclusion Key Name", "ObjectCount", false);
    protected Parameter[] parameters = new Parameter[]{structureContainer, structureToCount, percentageInclusion, onlyTrackHeads, inclusionText};
    
    public ObjectInclusionCount() {}
    
    public ObjectInclusionCount(int containingStructure, int structureToCount, double minPercentageInclusion) {
        this.structureContainer.setSelectedIndex(containingStructure);
        this.structureToCount.setSelectedIndex(structureToCount);
        this.percentageInclusion.setValue(minPercentageInclusion);
    }
    public ObjectInclusionCount setMeasurementName(String name) {
        inclusionText.setValue(name);
        return this;
    }
    public ObjectInclusionCount setOnlyTrackHeads(boolean onlyTh) {
        onlyTrackHeads.setSelected(onlyTh);
        return this;
    }
    @Override
    public int getCallStructure() {
        return structureContainer.getFirstCommonParentStructureIdx(structureToCount.getSelectedIndex());
    }
    // TODO: only track heads
    @Override
    public void performMeasurement(StructureObject object) {
        double p = percentageInclusion.getValue().doubleValue()/100d;
        if (object.getStructureIdx()==structureContainer.getSelectedIndex()) {
            object.getMeasurements().setValue(inclusionText.getValue(), count(object, structureToCount.getSelectedIndex(), p, onlyTrackHeads.getSelected()));
        } else {
            List<StructureObject> containers = object.getChildren(structureContainer.getSelectedIndex());
            List<StructureObject> toCount = object.getChildren(structureToCount.getSelectedIndex());
            for (StructureObject c : containers) {
                c.getMeasurements().setValue(inclusionText.getValue(), count(c, toCount, p, onlyTrackHeads.getSelected()));
            }
        }
        
        
    }
    
    @Override 
    public ArrayList<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<MeasurementKey>(1);
        res.add(new MeasurementKeyObject(inclusionText.getValue(), structureContainer.getSelectedIndex()));
        return res;
    }
    
    public static int count(StructureObject container, List<StructureObject> toCount, double proportionInclusion, boolean onlyTrackHeads) {
        if (toCount==null || toCount.isEmpty()) return 0;
        int count = 0;
        Region containerObject = container.getObject();
        for (StructureObject o : toCount) {
            if (onlyTrackHeads && !o.isTrackHead()) continue;
            if (o.getObject().intersect(containerObject)) {
                if (proportionInclusion==0) ++count;
                else {
                    if (o.getObject().getVoxels().isEmpty()) continue;
                    double incl = (double)o.getObject().getOverlapMaskMask(containerObject, null, null) / (double)o.getObject().getVoxels().size();
                    //logger.debug("inclusion: {}, threshold: {}, container: {}, parent:{}", incl, percentageInclusion, container, o.getParent());
                    if (incl>=proportionInclusion) ++count;
                }
            }
            //o.getObject().resetOffset();
        }
        //logger.debug("inclusion count: commont parent: {} container: {}, toTest: {}, result: {}", commonParent, container, toCount.size(), count);
        return count;
    }

    public static int count(StructureObject container, int structureToCount, double proportionInclusion, boolean onlyTrackHeads) {
        if (structureToCount==container.getStructureIdx()) return 1;
        int common = container.getExperiment().getFirstCommonParentStructureIdx(container.getStructureIdx(), structureToCount);
        StructureObject commonParent = container.getParent(common);

        List<StructureObject> toCount = commonParent.getChildren(structureToCount);
        return count(container, toCount, proportionInclusion, onlyTrackHeads);
        
    }
    
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }

    public boolean callOnlyOnTrackHeads() {
        return false;
    }
    
}
