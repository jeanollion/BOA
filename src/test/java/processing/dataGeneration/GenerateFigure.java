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

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import core.Task;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import ij.ImageJ;
import image.BoundingBox;
import image.Image;
import java.util.List;
import java.util.Map;
import processing.ImageFeatures;

/**
 *
 * @author jollion
 */
public class GenerateFigure {
    public static void main(String[] args) {
        new ImageJ();
        String db = "fluo170602_uvrD";
        BoundingBox bounds = new BoundingBox(0, 1000, 0, 300, 0, 0);
        generateImage(db, 0, 1, bounds);
    }
    private static void generateImage(String dbName, int posIdx, int mcIdx, BoundingBox bounds) {
        MasterDAO db = new Task(dbName).getDB();
        ObjectDAO dao = db.getDao(db.getExperiment().getPosition(posIdx).getName());
        List<StructureObject> roots = dao.getRoots();
        Map<StructureObject, List<StructureObject>> mc = StructureObjectUtils.getAllTracks(roots, 0);
        mc.entrySet().removeIf(e->e.getKey().getIdx()!=mcIdx);
        List<StructureObject> track = mc.entrySet().iterator().next().getValue();
        Image trackImage = track.get(0).getTrackImage(2);
        trackImage = trackImage.crop(bounds).setName("Raw");
        Image LoG = ImageFeatures.getLaplacian(trackImage, 2, true, false).setName("LoG");
        ImageWindowManagerFactory.showImage(trackImage);
        ImageWindowManagerFactory.showImage(LoG);
    }
            
}
