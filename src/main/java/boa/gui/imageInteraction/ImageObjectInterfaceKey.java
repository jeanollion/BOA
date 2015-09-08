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
package boa.gui.imageInteraction;

import dataStructure.objects.StructureObject;

/**
 *
 * @author jollion
 */
public class ImageObjectInterfaceKey {
    final boolean timeImage;
    final protected StructureObject parent;
    final protected int childStructureIdx;

    public ImageObjectInterfaceKey(StructureObject parent, int childStructureIdx, boolean timeImage) {
        this.timeImage = timeImage;
        this.parent = parent;
        this.childStructureIdx = childStructureIdx;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + (this.timeImage ? 1 : 0);
        hash = 71 * hash + (this.parent != null ? this.parent.hashCode() : 0);
        hash = 71 * hash + this.childStructureIdx;
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final ImageObjectInterfaceKey other = (ImageObjectInterfaceKey) obj;
        if (this.timeImage != other.timeImage) {
            return false;
        }
        if (this.parent != other.parent && (this.parent == null || !this.parent.equals(other.parent))) {
            return false;
        }
        if (this.childStructureIdx != other.childStructureIdx) {
            return false;
        }
        return true;
    }
    
}
