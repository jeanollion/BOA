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
package plugins.plugins.measurements;

import configuration.parameters.BooleanParameter;
import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import configuration.parameters.StructureParameter;
import configuration.parameters.TextParameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import static dataStructure.objects.StructureObjectUtils.getDaugtherObjectsAtNextFrame;
import image.BoundingBox;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import measurement.GeometricalMeasurements;
import measurement.MeasurementKey;
import measurement.MeasurementKeyObject;
import plugins.Measurement;
import plugins.ObjectFeature;
import plugins.objectFeature.ObjectFeatureCore;
import plugins.objectFeature.ObjectFeatureWithCore;
import plugins.plugins.measurements.objectFeatures.Size;
import utils.HashMapGetCreate;
import utils.LinearRegression;
import utils.MultipleException;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class GrowthRate implements Measurement {
    protected StructureParameter structure = new StructureParameter("Bacteria Structure", 1, false, false);
    protected PluginParameter<ObjectFeature> feature = new PluginParameter<>("Feature", ObjectFeature.class, new Size(), false).setToolTipText("Object Feature used to compute Growth Rate");
    protected TextParameter suffix = new TextParameter("Suffix", "", false).setToolTipText("Suffix added to measurement keys");
    protected BooleanParameter residuals = new BooleanParameter("Save Residuals", true);
    protected Parameter[] parameters = new Parameter[]{structure, feature, suffix, residuals};
    public GrowthRate() {}
    
    public GrowthRate(int structureIdx) {
        structure.setSelectedIndex(structureIdx);
    }
    
    public GrowthRate setSuffix(String suffix) {
        this.suffix.setValue(suffix);
        return this;
    }
    public GrowthRate setFeature(ObjectFeature f) {
        this.feature.setPlugin(f);
        this.suffix.setValue(f.getDefaultName());
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
        boolean res = residuals.getSelected();
        MultipleException ex = new MultipleException();
        final ArrayList<ObjectFeatureCore> cores = new ArrayList<>();
        HashMapGetCreate<StructureObject, ObjectFeature> ofMap = new HashMapGetCreate<>(p -> {
            ObjectFeature of = feature.instanciatePlugin().setUp(p, bIdx, p.getObjectPopulation(bIdx));
            if (of instanceof ObjectFeatureWithCore) ((ObjectFeatureWithCore)of).setUpOrAddCore(cores);
            return of;
        });
        List<StructureObject> parentTrack = StructureObjectUtils.getTrack(parentTrackHead, false);
        //Map<StructureObject, List<StructureObject>> bacteriaTracks = StructureObjectUtils.getAllTracksSplitDiv(parentTrack, bIdx);
        Map<StructureObject, List<StructureObject>> bacteriaTracks = StructureObjectUtils.getAllTracks(parentTrack, bIdx);
        long t3 = System.currentTimeMillis();
        for (List<StructureObject> l : bacteriaTracks.values()) {
            if (l.size()>=2) {
                double[] frame = new double[l.size()];
                double[] length = new double[frame.length];
                int idx = 0;
                for (StructureObject b : l) {
                    b.getObject().getVoxels();
                    b.getObject().getContour();
                }
                for (StructureObject b : l) {
                    frame[idx] = b.getCalibratedTimePoint();
                    length[idx++] = Math.log(ofMap.getAndCreateIfNecessary(b.getParent()).performMeasurement(b.getObject(), null));
                }
                double[] beta = LinearRegression.run(frame, length);
                double[] residuals = res? LinearRegression.getResiduals(frame, length, beta[0], beta[1]) : null;
                idx = 0;
                for (StructureObject b : l) {
                    b.getMeasurements().setValue("GrowthRate"+suffix, beta[1] );
                    b.getMeasurements().setValue("GrowthRateIntersection"+suffix, beta[0] );
                    if (res) b.getMeasurements().setValue("GrowthRateResidual"+suffix, residuals[idx++] );
                }
            }
        }
        long t4 = System.currentTimeMillis();
        logger.debug("GR: process: {}", t4-t3);
        if (!ex.isEmpty()) throw ex;
    }
    
    @Override 
    public ArrayList<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<>(3);
        String suffix = this.suffix.getValue();
        res.add(new MeasurementKeyObject("GrowthRate"+suffix, structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("GrowthRateIntersection"+suffix, structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("GrowthRateResidual"+suffix, structure.getSelectedIndex()));
        return res;
    }
    
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
}
