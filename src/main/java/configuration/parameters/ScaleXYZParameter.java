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

/**
 *
 * @author jollion
 */
public class ScaleXYZParameter extends SimpleContainerParameter {
    BoundedNumberParameter scaleXY = new BoundedNumberParameter("ScaleXY (pix)", 2, 1, 1, null);
    BoundedNumberParameter scaleZ = new BoundedNumberParameter("ScaleZ (pix)", 2, 1, 1, null);
    BooleanParameter useImageCalibration = new BooleanParameter("Use image calibration for Z-scale", true);
    ConditionalParameter cond = new ConditionalParameter(useImageCalibration);
    
    public ScaleXYZParameter() {
        super();
        init();
    }
    public ScaleXYZParameter(String name) {
        super(name);
        init();
    }
    public ScaleXYZParameter(String name, double scaleXY, double scaleZ, boolean useCalibration) {
        super(name);
        init();
        this.scaleXY.setValue(scaleXY);
        this.scaleZ.setValue(scaleZ);
        useImageCalibration.setSelected(useCalibration);
    }
    private void init() {
        cond.setAction("false", new Parameter[]{scaleZ});
    }
    @Override
    protected void initChildList() {
        super.initChildren(scaleXY, cond);
    }
    public double getScaleXY() {
        return scaleXY.getValue().doubleValue();
    }
    public double getScaleZ() {
        if (useImageCalibration.getSelected()) {
            MicroscopyField f = ParameterUtils.getMicroscopyFiedl(this);
            if (f==null) throw new Error("ScaleXYZParameter: no scale found in xp tree");
            return getScaleXY() * f.getScaleXY() / f.getScaleZ();
        } else return scaleZ.getValue().doubleValue();
    }
}
