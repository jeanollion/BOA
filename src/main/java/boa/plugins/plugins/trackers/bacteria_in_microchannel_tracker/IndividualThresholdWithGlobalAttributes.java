/*
 * Copyright (C) 2017 jollion
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
package boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker;

import boa.data_structure.StructureObject;
import ij.process.AutoThresholder;
import boa.image.Image;
import java.util.List;
import boa.plugins.OverridableThreshold;
import boa.plugins.Segmenter;
import boa.plugins.SimpleThresholder;
import boa.plugins.plugins.thresholders.IJAutoThresholder;
import static boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker.BacteriaClosedMicrochannelTrackerLocalCorrections.logger;

/**
 *
 * @author jollion
 */
public class IndividualThresholdWithGlobalAttributes extends ThresholdHisto {
    SimpleThresholder localThresholder; 
    SimpleThresholder globalThresholder;
    AutoThresholder.Method localMethod;
    public IndividualThresholdWithGlobalAttributes(List<Image> planes, int offsetFrame, AutoThresholder.Method localMethod, AutoThresholder.Method globalMethod) {
        super(planes, offsetFrame, globalMethod, null);
        this.localMethod=localMethod;
        this.thresholdF = new double[planes.size()];
        for (int i = 0; i<thresholdF.length; ++i) {
            double thld = IJAutoThresholder.runThresholder(localMethod, this.histos.get(i));
            thresholdF[i] = Math.min(thresholdValue, thld);
        }
    }
    
    @Override
    public boolean hasAdaptativeByY() {
        return false;
    }

    @Override
    public double getThreshold(int frame) {
        return thresholdF[frame-offsetFrame];
    }

    @Override
    public double getThreshold(int frame, int y) {
        return getThreshold(frame);
    }

    @Override public void setFrameRange(int[] fr) {
        if (fr==null) return;
        frameRange=new int[]{fr[0]-offsetFrame, fr[1]-offsetFrame};
    }
    
    @Override
    public void setAdaptativeByY(int yHalfWindow) {}

    @Override
    public void setAdaptativeThreshold(double adaptativeCoefficient, int adaptativeThresholdHalfWindow) {}
    
}
