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

import boa.data_structure.StructureObject;
import java.util.List;
import java.util.Map;

/**
 *
 * @author jollion
 */
public class ImageObjectInterfaceKey {
    public final boolean timeImage;
    public final List<StructureObject> parent;
    public final int displayedStructureIdx;

    public ImageObjectInterfaceKey(List<StructureObject> parent, int displayedStructureIdx, boolean timeImage) {
        this.timeImage = timeImage;
        this.parent = parent;
        this.displayedStructureIdx = displayedStructureIdx;
    }
    
    public ImageObjectInterfaceKey getKey(int childStructureIdx) {
        return new ImageObjectInterfaceKey(parent, childStructureIdx, timeImage);
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + (this.timeImage ? 1 : 0);
        hash = 71 * hash + (this.parent != null ? this.parent.hashCode() : 0);
        hash = 71 * hash + this.displayedStructureIdx;
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
        return this.displayedStructureIdx == other.displayedStructureIdx; 
    }
    
    @Override
    public String toString() {
        return parent.toString()+"/S="+displayedStructureIdx+"/Track="+timeImage;
    }
    
    public boolean equalsIgnoreStructure(Object obj) {
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
        return true;
    }
    public static <T> T getOneElementIgnoreStructure(ImageObjectInterfaceKey key, Map<ImageObjectInterfaceKey, T> map) {
        for (ImageObjectInterfaceKey k : map.keySet()) {
            if (k.equalsIgnoreStructure(key)) return map.get(k);
        }
        return null;
    }
}
