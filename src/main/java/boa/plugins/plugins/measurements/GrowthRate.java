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
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.StructureParameter;
import boa.configuration.parameters.TextParameter;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import static boa.data_structure.StructureObjectUtils.getDaugtherObjectsAtNextFrame;
import boa.image.MutableBoundingBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import boa.measurement.GeometricalMeasurements;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.GeometricalFeature;
import boa.plugins.Measurement;
import boa.plugins.MultiThreaded;
import boa.plugins.ObjectFeature;
import boa.plugins.object_feature.ObjectFeatureCore;
import boa.plugins.object_feature.ObjectFeatureWithCore;
import boa.plugins.plugins.measurements.objectFeatures.object_feature.Size;
import boa.utils.HashMapGetCreate;
import boa.utils.LinearRegression;
import boa.utils.MultipleException;
import boa.utils.Pair;
import static boa.utils.Utils.parallele;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author jollion
 */
public class GrowthRate implements Measurement, MultiThreaded {
    protected StructureParameter structure = new StructureParameter("Bacteria Structure", 1, false, false);
    protected PluginParameter<GeometricalFeature> feature = new PluginParameter<>("Feature", GeometricalFeature.class, new Size(), false).setToolTipText("Geometrical Feature of object used to compute Growth Rate");
    protected TextParameter suffix = new TextParameter("Suffix", "", false).setToolTipText("Suffix added to measurement keys");
    protected BooleanParameter intersection = new BooleanParameter("Save Intersection", false);
    protected BooleanParameter saveFeature = new BooleanParameter("Save Feature", false);
    protected TextParameter featureKey = new TextParameter("Feature Name", "", false).setToolTipText("Name given to geometrical feature in measurements");
    protected ConditionalParameter saveFeatureCond = new ConditionalParameter(saveFeature).setActionParameters("true", featureKey).setToolTipText("Whether value of geometrical feature should be saved to measurements");
    protected BoundedNumberParameter minCells = new BoundedNumberParameter("Minimum cell number", 0, 4, 3, null).setToolTipText("Minimum number of cell to compute growth rate. NA is returned if minimum not reached");
    protected Parameter[] parameters = new Parameter[]{structure, feature, minCells, suffix, saveFeatureCond, intersection};
    
    public GrowthRate() {
        feature.addListener( p -> {
            if (suffix.getValue().length()!=0 && featureKey.getValue().length()!=0) return;
            String n = feature.instanciatePlugin().getDefaultName();
            if (suffix.getValue().length()==0) suffix.setValue(n);
            if (featureKey.getValue().length()==0) featureKey.setValue(n);
        });
    }
    
    public GrowthRate(int structureIdx) {
        this();
        structure.setSelectedIndex(structureIdx);
    }
    
    public GrowthRate setSuffix(String suffix) {
        this.suffix.setValue(suffix);
        return this;
    }
    public GrowthRate setFeature(GeometricalFeature f) {
        this.feature.setPlugin(f);
        this.suffix.setValue(f.getDefaultName());
        this.featureKey.setValue(f.getDefaultName());
        return this;
    }
    
    @Override
    public int getCallStructure() {
        return structure.getParentStructureIdx();
    }
    
    @Override
    public boolean callOnlyOnTrackHeads() {
        return true;
    }
    @Override
    public void performMeasurement(StructureObject parentTrackHead) {
        int bIdx = structure.getSelectedIndex();
        String suffix = this.suffix.getValue();
        String featKey = this.featureKey.getValue();
        boolean inter = intersection.getSelected();
        boolean feat = saveFeature.getSelected();
        final ArrayList<ObjectFeatureCore> cores = new ArrayList<>();
        HashMapGetCreate<StructureObject, ObjectFeature> ofMap = new HashMapGetCreate<>(p -> {
            ObjectFeature of = feature.instanciatePlugin().setUp(p, bIdx, p.getObjectPopulation(bIdx));
            if (of instanceof ObjectFeatureWithCore) ((ObjectFeatureWithCore)of).setUpOrAddCore(cores, null);
            return of;
        });
        List<StructureObject> parentTrack = StructureObjectUtils.getTrack(parentTrackHead, false);
        parentTrack.stream().forEach(p->ofMap.getAndCreateIfNecessary(p));
        long t1 = System.currentTimeMillis();
        Map<StructureObject, Double> logLengthMap = parallele(StructureObjectUtils.getAllChildrenAsStream(parentTrack.stream(), bIdx), true).collect(Collectors.toMap(b->b, b->Math.log(ofMap.get(b.getParent()).performMeasurement(b.getRegion()))));
        long t2 = System.currentTimeMillis();
        Map<StructureObject, List<StructureObject>> bacteriaTracks = StructureObjectUtils.getAllTracks(parentTrack, bIdx);
        long t3 = System.currentTimeMillis();
        int minCells = this.minCells.getValue().intValue();
        parallele(bacteriaTracks.values().stream(), true).forEach(l-> {
            if (l.size()>=minCells) {
                double[] frame = new double[l.size()];
                double[] length = new double[frame.length];
                int idx = 0;
                for (StructureObject b : l) {
                    frame[idx] = b.getCalibratedTimePoint();
                    length[idx++] =   logLengthMap.get(b);
                }
                double[] beta = LinearRegression.run(frame, length);
                idx = 0;
                for (StructureObject b : l) {
                    b.getMeasurements().setValue("GrowthRate"+suffix, beta[1] );
                    if (inter) b.getMeasurements().setValue("GrowthRateIntersection"+suffix, beta[0] );
                    if (feat) b.getMeasurements().setValue(featKey, Math.exp(logLengthMap.get(b)));
                }
            } else { // erase values
                for (StructureObject b : l) {
                    b.getMeasurements().setValue("GrowthRate"+suffix, null );
                    if (inter) b.getMeasurements().setValue("GrowthRateIntersection"+suffix, null );
                    if (feat) b.getMeasurements().setValue(featKey, null);
                }
            }
        });
        long t4 = System.currentTimeMillis();
        logger.debug("Growth Rate: compute values: {}, process: {}", t2-t1, t4-t3);
    }
    
    @Override 
    public ArrayList<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<>(3);
        String suffix = this.suffix.getValue();
        res.add(new MeasurementKeyObject("GrowthRate"+suffix, structure.getSelectedIndex()));
        if (intersection.getSelected()) res.add(new MeasurementKeyObject("GrowthRateIntersection"+suffix, structure.getSelectedIndex()));
        if (this.saveFeature.getSelected()) res.add(new MeasurementKeyObject(featureKey.getValue(), structure.getSelectedIndex()));
        return res;
    }
    
    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    public static String getTrackHeadName(int trackHeadIdx) {
        //return String.valueOf(trackHeadIdx);
        int r = trackHeadIdx%24; // 24 : skip T & H 
        int mod = trackHeadIdx/24;
        if (r>=18) { // skip T
            trackHeadIdx+=2;
            if (r>=24) r = trackHeadIdx%24;
            else r+=2;
        } else if (r>=7) { // skip H
            trackHeadIdx+=1;
            r+=1;
        }
        
        char c = (char)(r + 65); //ASCII UPPER CASE +65
        
        if (mod>0) return String.valueOf(c)+mod;
        else return String.valueOf(c);
    }

    @Override
    public void setMultithread(boolean parallele) {
        // always true
    }
    
}
