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

import image.BoundingBox;
import image.Image;
import image.ImageIOCoordinates;
import image.ImageReader;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.PostLoad;

/**
 *
 * @author jollion
 */

@Embedded
public class ImageContainer {
    float scaleXY, scaleZ;
    ImageIOCoordinates ioCoordinates;
    String filePath;
    BoundingBox bounds;
    
    public ImageContainer(String filePath, Image image) {
        this.filePath=filePath;
        this.scaleXY=image.getScaleXY();
        this.scaleZ=image.getScaleZ();
        bounds=image.getBoundingBox();
        this.ioCoordinates=new ImageIOCoordinates();
    }
    
    public ImageContainer(String filePath, Image image, ImageIOCoordinates ioCoords) {
        this.filePath=filePath;
        this.scaleXY=image.getScaleXY();
        this.scaleZ=image.getScaleZ();
        this.ioCoordinates=ioCoords;
        if (ioCoordinates.getBounds()!=null) bounds=ioCoords.getBounds();
        else bounds=image.getBoundingBox();
    }
    
    public ImageContainer(String filePath, ImageIOCoordinates ioCoords, float scaleXY, float scaleZ) {
        this.filePath=filePath;
        this.ioCoordinates=ioCoords;
        this.bounds=ioCoords.getBounds();
        if (scaleXY!=0) this.scaleXY=scaleXY;
        if (scaleZ!=0) this.scaleZ=scaleZ;
    }
    
    public Image getImage() {
        if (bounds!=null) ioCoordinates.setBounds(bounds);
        Image image = ImageReader.openImage(filePath, ioCoordinates);
        if (scaleXY!=0 && scaleZ!=0) image.setCalibration(scaleXY, scaleZ);
        return image;
    }
    
    public Image getImage(BoundingBox bounds) {
        ImageIOCoordinates ioCoords = ioCoordinates.duplicate();
        ioCoords.setBounds(bounds);
        Image image = ImageReader.openImage(filePath, ioCoords);
        image.setCalibration(scaleXY, scaleZ);
        return image;
    }
    
    //morphia
    private ImageContainer(){};
    @PostLoad void postLoad() {ioCoordinates.setBounds(bounds);}
}


