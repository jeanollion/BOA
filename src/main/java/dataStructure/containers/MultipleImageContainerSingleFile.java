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

import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import image.BoundingBox;
import image.Image;
import image.ImageIOCoordinates;
import image.ImageReader;

/**
 *
 * @author jollion
 */

public class MultipleImageContainerSingleFile extends MultipleImageContainer {
    String filePath;
    String name;
    int timePointNumber, channelNumber;
    int seriesIdx;
    int sizeZ;
    BoundingBox bounds;
    @Transient private ImageReader reader;
    
    public MultipleImageContainerSingleFile(String name, String imagePath, int series, int timePointNumber, int channelNumber, int sizeZ) {
        this.name = name;
        this.seriesIdx=series;
        filePath = imagePath;
        this.timePointNumber = timePointNumber;
        this.channelNumber=channelNumber;
        this.sizeZ=sizeZ;
    }
    
    public MultipleImageContainerSingleFile duplicate() {
        return new MultipleImageContainerSingleFile(name, filePath, seriesIdx, timePointNumber, channelNumber, sizeZ);
    }
    
    public void setImagePath(String path) {
        this.filePath=path;
    }
    
    public String getFilePath(){return filePath;}
    
    public String getName(){return name;}

    public int getTimePointNumber() {
        return timePointNumber;
    }

    public int getChannelNumber() {
        return channelNumber;
    }
    /**
     * 
     * @param channelNumber ignored for this time of image container
     * @return the number of z-slices for each image
     */
    @Override
    public int getSizeZ(int channelNumber) {
        return sizeZ;
    }
    
    protected ImageIOCoordinates getImageIOCoordinates(int timePoint, int channel) {
        return new ImageIOCoordinates(seriesIdx, channel, timePoint);
    }
    
    protected ImageReader getReader() {
        if (reader==null) reader = new ImageReader(filePath);
        return reader;
    }
    
    @Override
    public synchronized Image getImage(int timePoint, int channel) {
        if (this.timePointNumber==1) timePoint=0;
        ImageIOCoordinates ioCoordinates = getImageIOCoordinates(timePoint, channel);
        if (bounds!=null) ioCoordinates.setBounds(bounds);
        Image image = getReader().openImage(ioCoordinates);
        if (scaleXY!=0 && scaleZ!=0) image.setCalibration((float)scaleXY, (float)scaleZ);
        else {
            scaleXY = image.getScaleXY();
            scaleZ = image.getScaleZ();
        }
        return image;
    }
    
    @Override
    public synchronized Image getImage(int timePoint, int channel, BoundingBox bounds) {
        if (this.timePointNumber==1) timePoint=0;
        ImageIOCoordinates ioCoordinates = getImageIOCoordinates(timePoint, channel);
        ImageIOCoordinates ioCoords = ioCoordinates.duplicate();
        ioCoords.setBounds(bounds);
        Image image = getReader().openImage(ioCoordinates);
        if (scaleXY!=0 && scaleZ!=0) image.setCalibration((float)scaleXY, (float)scaleZ);
        else {
            scaleXY = image.getScaleXY();
            scaleZ = image.getScaleZ();
        }
        return image;
    }
    public void close() {
        if (reader!=null) reader.closeReader();
        reader = null;
    }
}
