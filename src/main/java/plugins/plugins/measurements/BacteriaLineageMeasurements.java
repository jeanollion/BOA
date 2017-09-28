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

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.ChoiceParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.Parameter;
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
import utils.HashMapGetCreate;
import utils.LinearRegression;

/**
 *
 * @author jollion
 */
public class BacteriaLineageMeasurements implements Measurement {
    protected StructureParameter structure = new StructureParameter("Bacteria Structure", 1, false, false);
    protected TextParameter keyName = new TextParameter("Lineage Index Name", "LineageIndex", false);
    protected Parameter[] parameters = new Parameter[]{structure, keyName};
    public static char[] lineageName = new char[]{'H', 'T'};
    public static char lineageError = '*';
    public BacteriaLineageMeasurements() {}
    
    public BacteriaLineageMeasurements(int structureIdx) {
        structure.setSelectedIndex(structureIdx);
    }
    
    public BacteriaLineageMeasurements(int structureIdx, String keyName) {
        structure.setSelectedIndex(structureIdx);
        this.keyName.setValue(keyName);
    }
    
    @Override
    public int getCallStructure() {
        return structure.getParentStructureIdx();
    }
    
    @Override
    public boolean callOnlyOnTrackHeads() {
        return true;
    }
    private static List<StructureObject> getAllNextSortedY(StructureObject o, List<StructureObject> bucket) {
        bucket = getDaugtherObjectsAtNextFrame(o, bucket);
        Collections.sort(bucket, (o1, o2)->Double.compare(o1.getBounds().getyMin(), o2.getBounds().getyMin()));
        return bucket;
    }
    @Override
    public void performMeasurement(StructureObject parentTrackHead) {
        int bIdx = structure.getSelectedIndex();
        String key = this.keyName.getValue();
        
        HashMapGetCreate<StructureObject, List<StructureObject>> siblings = new HashMapGetCreate<>(o -> getAllNextSortedY(o, null));
        StructureObject currentParent = parentTrackHead;
        List<StructureObject> bacteria = currentParent.getChildren(bIdx);
        int trackHeadIdx = 0;
        for (StructureObject o : bacteria) {
            o.getMeasurements().setValue(key, getTrackHeadName(trackHeadIdx++));
            int nextTP = o.getNextDivisionTimePoint();
            o.getMeasurements().setValue("NextDivisionFrame", nextTP>=0?nextTP:null );
        }
        while(currentParent.getNext()!=null) {
            currentParent = currentParent.getNext();
            bacteria = currentParent.getChildren(bIdx);
            for (StructureObject o : bacteria) {
                if (o.getPrevious()==null) o.getMeasurements().setValue(key, getTrackHeadName(trackHeadIdx++));
                else {
                    List<StructureObject> sib = siblings.getAndCreateIfNecessary(o.getPrevious());
                    if (sib.size()==1 && Boolean.FALSE.equals(o.getPrevious().getAttribute("TruncatedDivision", false))) o.getMeasurements().setValue(key, o.getPrevious().getMeasurements().getValueAsString(key));
                    else {
                        if (sib.size()>2 || sib.isEmpty()) o.getMeasurements().setValue(key, o.getPrevious().getMeasurements().getValueAsString(key)+lineageError);
                        else if (sib.get(0).equals(o)) o.getMeasurements().setValue(key, o.getPrevious().getMeasurements().getValueAsString(key)+lineageName[0]);
                        else o.getMeasurements().setValue(key, o.getPrevious().getMeasurements().getValueAsString(key)+lineageName[1]);
                    }
                }
                //if (currentParent.getFrame()<=10 && currentParent.getIdx()==0) logger.debug("o: {}, prev: {}, next: {}, lin: {}", o, o.getPrevious(), siblings.getAndCreateIfNecessary(o.getPrevious()), o.getMeasurements().getValueAsString(key));
                int prevTP = o.getPreviousDivisionTimePoint();
                o.getMeasurements().setValue("PreviousDivisionFrame", prevTP>0 ? prevTP : null);
                int nextTP = o.getNextDivisionTimePoint();
                o.getMeasurements().setValue("NextDivisionFrame", nextTP>=0?nextTP:null );
            }
            siblings.clear();
        }
        
        List<StructureObject> parentTrack = StructureObjectUtils.getTrack(parentTrackHead, false);
        Map<StructureObject, List<StructureObject>> bacteriaTracks = StructureObjectUtils.getAllTracksSplitDiv(parentTrack, bIdx);
        for (List<StructureObject> l : bacteriaTracks.values()) {
            if (l.size()>=2) {
                double[] frame = new double[l.size()];
                double[] length = new double[frame.length];
                int idx = 0;
                for (StructureObject b : l) {
                    frame[idx] = b.getCalibratedTimePoint();
                    length[idx++] = Math.log(GeometricalMeasurements.getFeretMax(b.getObject()));
                }
                double[] beta = LinearRegression.run(frame, length);
                double[] residuals = LinearRegression.getResiduals(frame, length, beta[0], beta[1]);
                idx = 0;
                for (StructureObject b : l) {
                    b.getMeasurements().setValue("GrowthRate", beta[1] );
                    b.getMeasurements().setValue("GrowthRateIntersection", beta[0] );
                    b.getMeasurements().setValue("GrowthRateResidual", residuals[idx++] );
                }
                /*if (!debug && l.size()>=5) {
                    debug = true;
                    logger.debug("frames: {}", frame);
                    logger.debug("length: {}", length);
                    logger.debug("intersect: {}, slope: {}", beta[0], beta[1]);
                    logger.debug("residuals: {}", residuals);
                }*/
            }
        }
    }
    
    @Override 
    public ArrayList<MeasurementKey> getMeasurementKeys() {
        ArrayList<MeasurementKey> res = new ArrayList<>(4);
        res.add(new MeasurementKeyObject(keyName.getValue(), structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("NextDivisionFrame", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("PreviousDivisionFrame", structure.getSelectedIndex()));
        res.add(new MeasurementKeyObject("GrowthRate", structure.getSelectedIndex()));
        //res.add(new MeasurementKeyObject("GrowthRateIntersection", structure.getSelectedIndex()));
        //res.add(new MeasurementKeyObject("GrowthRateResidual", structure.getSelectedIndex()));
        return res;
    }
    
    public Parameter[] getParameters() {
        return parameters;
    }

    public boolean does3D() {
        return true;
    }
    private static String getTrackHeadName(int trackHeadIdx) {
        //return String.valueOf(trackHeadIdx);
        char c = (char)(trackHeadIdx%26 + 65); //ASCII UPPER CASE +65
        int mod = trackHeadIdx/26;
        if (mod>0) return String.valueOf(c)+mod;
        else return String.valueOf(c);
    }
}
