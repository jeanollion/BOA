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
package dataStructure.containers;

import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import image.Image;
import static image.Image.logger;
import image.ImageInteger;

/**
 *
 * @author jollion
 */

@Embedded(polymorph=true)
public class ObjectContainerImage extends ObjectContainer {

    public ObjectContainerImage(StructureObject structureObject) {
        super(structureObject);
    }

    public ImageInteger getImage() {
        Image image = structureObject.getExperiment().getImageDAO().openMask((StructureObject)structureObject);
        return (ImageInteger) image.resetOffset().addOffset(bounds.getxMin(), bounds.getyMin(), bounds.getzMin()).setCalibration(getScaleXY(), getScaleZ());
    }

    public void updateObject() {
        structureObject.getExperiment().getImageDAO().writeMask(structureObject.getObject().getMask(), (StructureObject)structureObject);
    }
    
    public Object3D getObject() { 
        ImageInteger mask = getImage();
        return new Object3D(mask, structureObject.getIdx()+1);
    }

    @Override
    public void deleteObject() {
        structureObject.getExperiment().getImageDAO().deleteMask(structureObject);
    }
    
    @Override
    public void relabelObject(int newIdx) {
        structureObject.getExperiment().getImageDAO().renameMask(structureObject, newIdx);
    }
}