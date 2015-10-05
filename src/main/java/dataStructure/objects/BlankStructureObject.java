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
package dataStructure.objects;

import image.Image;
import image.ImageMask;

/**
 *
 * @author jollion
 */
public class BlankStructureObject implements StructureObjectProcessing {
    ImageMask mask;
    public BlankStructureObject(ImageMask mask) {
        this.mask=mask;
    }
    
    public Image getRawImage(int structureIdx) {
        return null;
    }

    public ImageMask getMask() {
        return mask;
    }

    public StructureObjectProcessing getNext() {
        return null;
    }

    public int getTimePoint() {
        return 0;
    }

    public StructureObjectProcessing getParent() {
        return null;
    }

    public StructureObjectProcessing getPrevious() {
        return null;
    }

    public void setPreviousInTrack(StructureObjectPreProcessing previous, boolean isTrackHead) {
        
    }

    public Image getFilteredImage(int structureIdx) {
        return null;
    }

    public Object3D getObject() {
        return null;
    }
    
}
