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
package plugins.plugins.trackers.trackMate;

import boa.gui.imageInteraction.IJImageWindowManager.Roi3D;
import boa.gui.imageInteraction.ImageWindowManager;
import dataStructure.objects.StructureObject;
import ij.gui.Roi;
import ij.gui.TextRoi;
import image.BoundingBox;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import static plugins.Plugin.logger;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class SpotWithinCompartmentRoiModifier implements ImageWindowManager.RoiModifier<Roi3D> {
    TrackMateInterface<SpotWithinCompartment> tmi;
    
    public SpotWithinCompartmentRoiModifier(TrackMateInterface<SpotWithinCompartment> tmi) {
        this.tmi=tmi;
        TextRoi.setFont("SansSerif", 6, Font.PLAIN);
    }
    @Override
    public void modifyRoi(Pair<StructureObject, BoundingBox> currentObject, Roi3D currentRoi, Collection<Pair<StructureObject, BoundingBox>> objectsToDisplay) {
        Roi r = currentRoi.get(currentObject.value.getzMin());
        currentRoi.clear();
        currentRoi.put(0, r);
        if (objectsToDisplay.size()>10) return;
        SpotWithinCompartment s = tmi.objectSpotMap.get(currentObject.key.getObject());
        if (s==null) return;
        //else logger.debug("spot found for: {} loc: {}", currentObject.key, s.localization);
        TextRoi txt = s.getLocalizationRoi(currentObject.value);
        int idx = 0;
        currentRoi.put(++idx, txt); // hack for 2D case !
        SpotWithinCompartment.displayPoles=true;
        SpotWithinCompartment.rois=new ArrayList<>(6);
        for (Pair<StructureObject, BoundingBox> p : objectsToDisplay) {
            if (p.key.equals(currentObject.key)) continue;
            SpotWithinCompartment s2 = tmi.objectSpotMap.get(p.key.getObject());
            if (s2==null) continue;
            if (p.key.getFrame()<currentObject.key.getFrame()) {
                SpotWithinCompartment.offsetS1 = p.value;
                SpotWithinCompartment.offsetS2 = currentObject.value;
            } else {
                SpotWithinCompartment.offsetS2 = p.value;
                SpotWithinCompartment.offsetS1 = currentObject.value;
            }
            s2.squareDistanceTo(s);
            logger.debug("distance: {}->{}, rois: {}", p.key, currentObject.key, SpotWithinCompartment.rois);
            for (Roi rr : SpotWithinCompartment.rois) currentRoi.put(++idx, rr);
            SpotWithinCompartment.rois.clear();
        }
        SpotWithinCompartment.rois=null;
        
    }
    
}
