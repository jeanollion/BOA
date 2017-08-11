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
package processing.dataGeneration;

import boa.gui.imageInteraction.IJImageWindowManager;
import boa.gui.imageInteraction.ImageObjectInterface;
import boa.gui.imageInteraction.ImageWindowManager;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Task;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import static dataStructure.objects.StructureObjectUtils.getInclusionParentMap;
import ij.ImagePlus;
import ij.process.ImageConverter;
import image.BoundingBox;
import image.Image;
import java.awt.Color;
import java.util.List;
import java.util.Map;
import measurement.BasicMeasurements;
import utils.Palette;
import static utils.Utils.flattenMap;
import static utils.Utils.getFirst;
import static utils.Utils.removeIf;

/**
 *
 * @author jollion
 */
public class GenerateImages {
    public static void main(String[] args) {
        String dbName = "boa_fluo151127";
        int positionIdx = 1;
        int mcIdx = 1;
        int tStart = 190;
        int tEnd = 209;
        int yMin = 20;
        int yMax = 180;
        double meanIntensityThld = 135;
        MasterDAO db = new Task(dbName).getDB();
        ObjectDAO dao = db.getDao(db.getExperiment().getPosition(positionIdx).getName());
        List<StructureObject> roots = dao.getRoots();
        List<StructureObject> mcTrack = getFirst(StructureObjectUtils.getAllTracks(roots, 0), o->o.getIdx()==mcIdx);
        Map<StructureObject, List<StructureObject>> allBacts = StructureObjectUtils.getAllTracksSplitDiv(mcTrack, 1);
        Map<StructureObject, List<StructureObject>> allMuts = StructureObjectUtils.getAllTracks(mcTrack, 2);
        //for (List<StructureObject> l : allBacts.values()) l.removeIf(o->o.getFrame()<tStart||o.getFrame()>tEnd||o.getIdx()>1);
        allBacts.values().removeIf(l->l.isEmpty());
        //Map<StructureObject, StructureObject> parentMap = getInclusionParentMap(flattenMap(allMuts), 1);
        Map<StructureObject, StructureObject> parentMap = getInclusionParentMap(flattenMap(allMuts), flattenMap(allBacts));
        for (List<StructureObject> l : allMuts.values()) l.removeIf(o->o.getFrame()<tStart||o.getFrame()>tEnd||parentMap.get(o)==null || parentMap.get(o).getIdx()>1 || parentMap.get(o).getPrevious().getIdx()>0 || BasicMeasurements.getMeanValue(o.getObject(), o.getParent().getRawImage(2), true)<meanIntensityThld);
        allMuts.values().removeIf(l->l.isEmpty());
        mcTrack.removeIf(o -> o.getFrame()<tStart||o.getFrame()>tEnd);
        IJImageWindowManager iwm = (IJImageWindowManager)ImageWindowManagerFactory.getImageManager();
        
        ImageObjectInterface i = iwm.getImageTrackObjectInterface(mcTrack, 1);
        Image imBact = i.generateRawImage(1, false);
        Image imMut = i.generateRawImage(2, false);
        BoundingBox crop = new BoundingBox(0, imBact.getSizeX()-1, 0, yMax, 0, 0);
        imBact=imBact.crop(crop);
        imMut=imMut.crop(crop);
        iwm.addImage(imMut, i, 2, false, true);
        Palette.currentColorIdx=0.5;
        Palette.increment=0.1;
        iwm.displayTracks(imMut, i, allMuts.values(), true);
        iwm.getDisplayer().getImage(imMut).setDisplayRange(110, 200);
        
        //iwm.displayObjects(imMut, i.pairWithOffset(flattenMap(allMuts)), Color.yellow, true, false);
        ImagePlus merge = ij.plugin.RGBStackMerge.mergeChannels(new ImagePlus[]{iwm.getDisplayer().getImage(imBact), iwm.getDisplayer().getImage(imMut)}, true);
        merge.setOverlay(((ImagePlus)iwm.getDisplayer().getImage(imMut)).getOverlay());
        merge.show();
    }
}
