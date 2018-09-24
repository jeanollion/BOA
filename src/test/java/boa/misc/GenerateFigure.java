/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.misc;

import boa.gui.image_interaction.ImageWindowManagerFactory;
import boa.core.Task;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import ij.ImageJ;
import boa.image.MutableBoundingBox;
import boa.image.Image;
import java.util.List;
import java.util.Map;
import boa.image.processing.ImageFeatures;

/**
 *
 * @author jollion
 */
public class GenerateFigure {
    public static void main(String[] args) {
        new ImageJ();
        String db = "fluo160428";
        MutableBoundingBox bounds = new MutableBoundingBox(0, 1000, 0, 300, 0, 0);
        generateImage(db, 0, 1, bounds);
    }
    private static void generateImage(String dbName, int posIdx, int mcIdx, MutableBoundingBox bounds) {
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
