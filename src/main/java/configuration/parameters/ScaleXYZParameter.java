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
package configuration.parameters;

import dataStructure.configuration.MicroscopyField;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.Track;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import de.caluga.morphium.annotations.lifecycle.PostLoad;

/**
 *
 * @author jollion
 */
@Lifecycle
public class ScaleXYZParameter extends SimpleContainerParameter {
    BoundedNumberParameter scaleXY = new BoundedNumberParameter("ScaleXY (pix)", 2, 1, 1, null);
    BoundedNumberParameter scaleZ = new BoundedNumberParameter("ScaleZ (pix)", 2, 1, 1, null);
    BooleanParameter useImageCalibration = new BooleanParameter("Use image calibration for Z-scale", true);
    @Transient ConditionalParameter cond; // init occurs @ construction or @ postLoad
        
    public ScaleXYZParameter(String name) {
        super(name);
        init();
    }
    public ScaleXYZParameter(String name, double scaleXY, double scaleZ, boolean useCalibration) {
        super(name);
        this.scaleXY.setValue(scaleXY);
        this.scaleZ.setValue(scaleZ);
        useImageCalibration.setSelected(useCalibration);
        init();
    }
    protected void init() { 
        cond = new ConditionalParameter(useImageCalibration);
        cond.setActionParameters(false, new Parameter[]{scaleZ}, false);
        //logger.debug("init scaleXYZParameter:{} XY:{}, Z:{}, use: {}", this.hashCode(), scaleXY.getValue(), scaleZ.getValue(), useImageCalibration.getValue());
    }
    @Override
    protected void initChildList() {
        super.initChildren(scaleXY, cond);
    }
    public double getScaleXY() {
        return scaleXY.getValue().doubleValue();
    }
    public double getScaleZ(Track o) {
        if (useImageCalibration.getSelected()) {
            //MicroscopyField f = ParameterUtils.getMicroscopyFiedl(this);
            //if (f==null) throw new Error("ScaleXYZParameter: no scale found in xp tree");
            if (o==null) return scaleZ.getValue().doubleValue();
            return getScaleXY() * o.getScaleXY() / o.getScaleZ();
        } else return scaleZ.getValue().doubleValue();
    }
    
    @Override public void setContentFrom(Parameter other) { // need to override because the super class's method only set the content from children parameters (children parameter = transient conditional parameter)
        if (other instanceof ScaleXYZParameter) {
            ScaleXYZParameter otherP = (ScaleXYZParameter) other;
            scaleXY.setContentFrom(otherP.scaleXY);
            scaleZ.setContentFrom(otherP.scaleZ);
            useImageCalibration.setContentFrom(useImageCalibration);
        } else {
            throw new IllegalArgumentException("wrong parameter type");
        }
    }
    
    @Override 
    @PostLoad  public void postLoad() {
        //logger.debug("ScaleXXY postLoad call for : {}", this.hashCode());
        if (!postLoaded) {
            init();
            initChildList();
            postLoaded=true;
        }
    }
    public ScaleXYZParameter() {
        super(); 
        //logger.debug("init null constructor scaleXYZParameter:{} XY:{}, Z:{}, use: {}", this.hashCode(), scaleXY.getValue(), scaleZ.getValue(), useImageCalibration.getValue());
        // init of conditional parameter will occur @ postLoad
    }
}
