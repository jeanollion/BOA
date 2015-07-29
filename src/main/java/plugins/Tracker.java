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
package plugins;

import dataStructure.objects.StructureObjectAbstract;
import dataStructure.objects.StructureObjectPostProcessing;
import dataStructure.objects.StructureObjectPreProcessing;
import dataStructure.objects.StructureObjectProcessing;
import dataStructure.objects.Track;

/**
 *
 * @author jollion
 */
public interface Tracker extends Plugin {
    /**
     * assign {@param previous} to {@param next} using the method {@link StructureObjectAbstract#setParentTrack(dataStructure.objects.Track, boolean) }
     * @param previous objects that share a given parent object
     * @param next objects that share a given parent object that is the next object of the parent of the {@param previous} objects
     */
    public void assignPrevious(StructureObjectPreProcessing[] previous, StructureObjectPreProcessing[] next);
}
