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
import image.BoundingBox;
import image.Image;
import image.ImageIOCoordinates;
import image.ImageInteger;
import org.mongodb.morphia.annotations.Embedded;

/**
 *
 * @author jollion
 */
@Embedded
public class ImageIntegerContainer extends ImageContainer implements ObjectContainer {
    int label=1;
    
    public ImageIntegerContainer(String filePath, ImageInteger image) {
        super(filePath, image);
    }
    
    public ImageIntegerContainer(String filePath, ImageInteger image, int label) {
        this(filePath, image);
        this.label=label;
    }
    
    public ImageIntegerContainer(String filePath, ImageInteger image, ImageIOCoordinates ioCoords) {
        super(filePath, image, ioCoords);
    }
    @Override
    public ImageInteger getImage() {
        return (ImageInteger) super.getImage();
    }
    
    @Override
    public ImageInteger getImage(BoundingBox bounds) {
        return (ImageInteger) super.getImage(bounds);
    }
    
    public Object3D getObject() { // attension s'assurer que l'image ne contient qu'un seul objet et bounds(image) = bounds(object)
        ImageInteger mask = getImage();
        return new Object3D(mask, label);
    }
    
}
