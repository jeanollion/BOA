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
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import image.BoundingBox;
import image.Image;
import image.ImageFormat;
import image.ImageIOCoordinates;
import image.ImageInteger;
import image.ImageReader;
import image.ImageWriter;

/**
 *
 * @author jollion
 */

@Embedded(polymorph=true)
public class ObjectContainerImage extends ObjectContainer {
    int label;
    @Transient String filePath;
    public ObjectContainerImage(Object3D object, String filePath) {
        super(object.getBounds(), object.getScaleXY(), object.getScaleZ());
        this.label=object.getLabel();
        this.filePath = filePath;
    }

    public ImageInteger getImage() {
        Image image = ImageReader.openImage(filePath, new ImageIOCoordinates());
        image.setOffset(bounds.getxMin(), bounds.getyMin(), bounds.getzMin());
        image.setCalibration(scaleXY, scaleZ);
        return (ImageInteger)image;
    }

    public void updateObject(Object3D object) {
        ImageWriter.writeToFile(object.getMask(), filePath, ImageFormat.PNG);
    }
    
    public Object3D getObject() { 
        ImageInteger mask = getImage();
        return new Object3D(mask, label);
    }
    
    //morphium
    public ObjectContainerImage(){};

}