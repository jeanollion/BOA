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
package plugins.plugins.trackers;

import boa.gui.imageInteraction.ImageObjectInterface;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectTracker;
import java.util.ArrayList;
import java.util.List;
import plugins.Tracker;
import plugins.plugins.trackers.trackMate.LAPTrackerCore;
import plugins.plugins.trackers.trackMate.SpotPopulation;

/**
 *
 * @author jollion
 */
public class LAPTracker implements Tracker {
    static int compartimentStructureIdx = 1;
    public void track(int structureIdx, List<StructureObject> parentTrack) {
        SpotPopulation spotCollection = new SpotPopulation();
        for (StructureObject p : parentTrack) {
            spotCollection.addSpots(p, structureIdx, compartimentStructureIdx);
        }
        LAPTrackerCore core = new LAPTrackerCore(spotCollection);
        boolean processOk = core.process();
        if (!processOk) logger.error("LAPTracker error : {}", core.getErrorMessage());
        else spotCollection.setTrackLinks(parentTrack, structureIdx, core.getEdges());
    }

    public Parameter[] getParameters() {
        return new Parameter[0];
    }
    
}
