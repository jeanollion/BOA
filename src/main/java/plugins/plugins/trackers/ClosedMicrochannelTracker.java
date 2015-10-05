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
package plugins.plugins.trackers;

import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectPreProcessing;
import measurement.GeometricalMeasurements;
import plugins.Tracker;

/**
 *
 * @author jollion
 */
public class ClosedMicrochannelTracker implements Tracker {
    
    public void assignPrevious(StructureObjectPreProcessing[] previous, StructureObjectPreProcessing[] next) {
        int counterPrevious=0;
        int counterNext=0;
        double[] previousSize = new double[previous.length];
        for (int i = 0; i<previousSize.length; ++i) previousSize[i] = GeometricalMeasurements.getVolume(previous[i].getObject());
        double[] nextSize = new double[next.length];
        for (int i = 0; i<previousSize.length; ++i) nextSize[i] = GeometricalMeasurements.getVolume(next[i].getObject());
        while(counterNext<next.length) {
            
        }
    }

    public Parameter[] getParameters() {
        
    }

    public boolean does3D() {
        return true;
    }
    
}
