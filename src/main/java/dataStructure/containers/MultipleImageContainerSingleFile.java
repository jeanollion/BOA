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
import java.util.Arrays;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import utils.JSONUtils;

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
    
    @Override
    public boolean sameContent(MultipleImageContainer other) {
        if (other instanceof MultipleImageContainerSingleFile) {
            MultipleImageContainerSingleFile otherM = (MultipleImageContainerSingleFile)other;
            if (scaleXY!=otherM.scaleXY) return false;
            if (scaleZ!=otherM.scaleZ) return false;
            if (!name.equals(otherM.name)) return false;
            if (!filePath.equals(otherM.filePath)) return false;
            if (timePointNumber!=otherM.timePointNumber) return false;
            if (channelNumber!=otherM.channelNumber) return false;
            if (seriesIdx!=otherM.seriesIdx) return false;
            if (sizeZ!=otherM.sizeZ) return false;
            if (bounds!=null && !bounds.equals(otherM.bounds)) return false;
            else if (bounds==null && otherM.bounds!=null) return false;
            return true;
        } else return false;
    }
    
    @Override
    public Object toJSONEntry() {
        JSONObject res = new JSONObject();
        res.put("scaleXY", scaleXY);
        res.put("scaleZ", scaleZ);
        res.put("filePath", filePath);
        res.put("name", name);
        res.put("framePointNumber", timePointNumber);
        res.put("channelNumber", channelNumber);
        res.put("seriesIdx", seriesIdx);
        res.put("sizeZ", sizeZ);
        if (bounds!=null) res.put("bounds", bounds.toJSONEntry());
        return res;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        JSONObject jsonO = (JSONObject)jsonEntry;
        scaleXY = ((Number)jsonO.get("scaleXY")).doubleValue();
        scaleZ = ((Number)jsonO.get("scaleZ")).doubleValue();
        filePath = (String)jsonO.get("filePath");
        name = (String)jsonO.get("name");
        timePointNumber = ((Number)jsonO.get("framePointNumber")).intValue();
        channelNumber = ((Number)jsonO.get("channelNumber")).intValue();
        seriesIdx = ((Number)jsonO.get("seriesIdx")).intValue();
        sizeZ = ((Number)jsonO.get("sizeZ")).intValue();
        if (jsonO.containsKey("bounds")) {
            bounds = new BoundingBox();
            bounds.initFromJSONEntry(jsonO.get(("bounds")));
        }
    }
    protected MultipleImageContainerSingleFile() {super(1, 1);}
    public MultipleImageContainerSingleFile(String name, String imagePath, int series, int timePointNumber, int channelNumber, int sizeZ, double scaleXY, double scaleZ) {
        super(scaleXY, scaleZ);
        this.name = name;
        this.seriesIdx=series;
        filePath = imagePath;
        this.timePointNumber = timePointNumber;
        this.channelNumber=channelNumber;
        this.sizeZ=sizeZ;
    }
    
    @Override public MultipleImageContainerSingleFile duplicate() {
        return new MultipleImageContainerSingleFile(name, filePath, seriesIdx, timePointNumber, channelNumber, sizeZ, scaleXY, scaleZ);
    }
    
    @Override public double getCalibratedTimePoint(int t, int c, int z) {
        return getReader().getTimePoint(c, t, z);
    }
    
    public void setImagePath(String path) {
        this.filePath=path;
    }
    
    public String getFilePath(){return filePath;}
    
    public String getName(){return name;}

    public int getFrameNumber() {
        return timePointNumber;
    }

    public int getChannelNumber() {
        return channelNumber;
    }
    
    @Override
    public boolean singleFrame(int channel) {
        return false;
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
        /*if (scaleXY!=0 && scaleZ!=0) image.setCalibration((float)scaleXY, (float)scaleZ);
        else {
            scaleXY = image.getScaleXY();
            scaleZ = image.getScaleZ();
        }*/
        return image;
    }
    
    @Override
    public synchronized Image getImage(int timePoint, int channel, BoundingBox bounds) {
        if (this.timePointNumber==1) timePoint=0;
        ImageIOCoordinates ioCoordinates = getImageIOCoordinates(timePoint, channel);
        ImageIOCoordinates ioCoords = ioCoordinates.duplicate();
        ioCoords.setBounds(bounds);
        Image image = getReader().openImage(ioCoordinates);
        /*if (scaleXY!=0 && scaleZ!=0) image.setCalibration((float)scaleXY, (float)scaleZ);
        else {
            scaleXY = image.getScaleXY();
            scaleZ = image.getScaleZ();
        }*/
        return image;
    }
    public void close() {
        if (reader!=null) reader.closeReader();
        reader = null;
    }
}
