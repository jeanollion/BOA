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
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.Track;
import plugins.Tracker;

/**
 *
 * @author jollion
 */
public class TrackerObjectIdx implements Tracker {

    public void assignPrevious(StructureObjectPreProcessing[] parents, StructureObjectPreProcessing[] children) {
        for (int i = 0; i<Math.min(parents.length, children.length); ++i) {
            parents[i].setPreviousInTrack(children[i], true);
        }
    }

    public Parameter[] getParameters() {
        return new Parameter[0];
    }

    public boolean does3D() {
        return true;
    }
    
}