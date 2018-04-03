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
package boa.configuration.parameters;

import boa.configuration.parameters.ui.ParameterUI;

/**
 *
 * @author jollion
 */
public class FrameParameter extends BoundedNumberParameter {
    private int frameNumber=-1;
    boolean useRawInputFrames;
    
    public FrameParameter(String name, int defaultTimePoint, boolean useRawInputFrames) {
        super(name, 0, defaultTimePoint, 0, null);
        this.useRawInputFrames=useRawInputFrames;
    }
    public FrameParameter(String name, int defaultTimePoint) {
        this(name, defaultTimePoint, false);
    }
    public FrameParameter(String name) {
        this(name, 0, false);
    }
    public FrameParameter() {this("");}
    
    public void setMaxFrame(int maxFrame) {
        super.upperBound = maxFrame;
        frameNumber = maxFrame+1;
    }
    public int getMaxFrame() {
        return Math.max(0, frameNumber-1);
    }

    public void setUseRawInputFrames(boolean useRawInputFrames) {
        this.useRawInputFrames = useRawInputFrames;
    }
    
    public void setFrame(int timePoint) {
        super.setValue(timePoint);
    }
    
    private int checkWithBounds(int timePoint) {
        int max = getMaxFrame();
        if (max>=0) {
            if (timePoint>max) return Math.max(0, max);
            else return Math.max(0, timePoint);
        } else return 0;
    }
    
    public int getSelectedFrame() {
        return checkWithBounds(super.getValue().intValue());
    }
    
    @Override public ParameterUI getUI() {
        getMaxFrame(); // sets the upper bound
        return super.getUI();
    }
    
}
