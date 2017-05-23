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
package boa.gui.imageInteraction;

import dataStructure.objects.StructureObject;
import image.BoundingBox;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import utils.Pair;

/**
 *
 * @author jollion
 */
public class ManualObjectStrecher {
    public static final Logger logger = LoggerFactory.getLogger(ManualObjectStrecher.class);
    public static void strechObjects(List<Pair<StructureObject, BoundingBox>> parents, int structureIdx, int[] xPoints, int[] yPoints) {
        logger.debug("will strech {} objects, of structure: {}, x: {}, y: {}", parents.size(), structureIdx, xPoints, yPoints);
        // séparer la ligne en voxels par parent 
        // pour chaque objet: le point le plus à gauche et en haut -> le point le plus proche de la ligne inclut da
        
    }
}
