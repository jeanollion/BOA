/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins.plugins.measurements;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ObjectClassParameter;
import boa.data_structure.StructureObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import boa.measurement.MeasurementKey;
import boa.measurement.MeasurementKeyObject;
import boa.plugins.DevPlugin;
import boa.plugins.Measurement;
import boa.plugins.plugins.transformations.SelectBestFocusPlane;


/**
 *
 * @author Jean Ollion
 */
public class XZSlope implements Measurement, DevPlugin {
    protected ObjectClassParameter microchannel = new ObjectClassParameter("Microchannel Structure", 0, false, false);
    @Override
    public int getCallObjectClassIdx() {
        return -1;
    }

    @Override
    public boolean callOnlyOnTrackHeads() {
        return false;
    }

    @Override
    public List<MeasurementKey> getMeasurementKeys() {
        return new ArrayList<MeasurementKey>(){{add(new MeasurementKeyObject("XZSlope", 0));add(new MeasurementKeyObject("FocusPlane", 0));}};
    }

    @Override
    public void performMeasurement(StructureObject object) {
        List<StructureObject> mcs = object.getChildren(0);
        if (mcs.size()<2) return;
        Collections.sort(mcs, (o1, o2)->Integer.compare(o1.getBounds().xMin(), o2.getBounds().xMin()));
        StructureObject oLeft = mcs.get(0);
        StructureObject oRight = mcs.get(mcs.size()-1);
        int left = SelectBestFocusPlane.getBestFocusPlane(oLeft.getRawImage(oLeft.getStructureIdx()).splitZPlanes(), 3, null, null);
        int right = SelectBestFocusPlane.getBestFocusPlane(oRight.getRawImage(oLeft.getStructureIdx()).splitZPlanes(), 3, null, null);
        double value = (right-left) * object.getScaleZ() / ((oRight.getBounds().xMean()-oLeft.getBounds().xMean()) * object.getScaleXY());
        logger.debug("focus plane left: {} right: {} value: {} (scale XY: {}, Z: {})", left, right, value, object.getScaleXY(), object.getScaleZ());
        oLeft.getMeasurements().setValue("XZSlope", value);
        oRight.getMeasurements().setValue("XZSlope", value);
        oLeft.getMeasurements().setValue("FocusPlane", left);
        oRight.getMeasurements().setValue("FocusPlane", right);
    }

    @Override
    public Parameter[] getParameters() {
        return new Parameter[]{microchannel};
    }
    
}
