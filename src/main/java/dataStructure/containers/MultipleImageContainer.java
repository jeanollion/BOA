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

import image.ImageIOCoordinates;
import image.ImageReader;
import org.mongodb.morphia.annotations.Embedded;

/**
 *
 * @author jollion
 */
@Embedded
public class MultipleImageContainer {
    String imagesPath;
    String name;
    int timePointNumber, channelNumber;
    int serie;
    FileType fileType;
    
    public MultipleImageContainer(String name, String imagePath, int serie, int timePointNumber, int channelNumber) {
        fileType = FileType.SINGLE_FILE;
        this.name = name;
        this.serie=serie;
        imagesPath = imagePath;
        this.timePointNumber = timePointNumber;
        this.channelNumber=channelNumber;
    }
    
    public String getPath(){return imagesPath;}
    
    public String getName(){return name;}

    public int getTimePointNumber() {
        return timePointNumber;
    }

    public int getChannelNumber() {
        return channelNumber;
    }
    
    
    public ImageIOCoordinates[][] getImageIOCoordinatesTC() {
        ImageIOCoordinates[][] icTC = new ImageIOCoordinates[timePointNumber][channelNumber];
        if (fileType.equals(FileType.SINGLE_FILE)) {
            for (int t = 0; t < timePointNumber; t++) {
                for (int c = 0; c < channelNumber; c++) {
                    icTC[t][c] = new ImageIOCoordinates(serie, c, t);
                }
            }
            return icTC;
        } else return null;
    }
    
    public ImageIOCoordinates[] getImageIOCoordinatesC(int timePoint) {
        ImageIOCoordinates[] icC = new ImageIOCoordinates[channelNumber];
        if (fileType.equals(FileType.SINGLE_FILE)) {
            for (int c = 0; c < channelNumber; c++) {
                icC[c] = new ImageIOCoordinates(serie, c, timePoint);
            }   
            return icC;
        } else return null;
    }
    
    public ImageIOCoordinates getImageIOCoordinates(int timePoint, int channel) {
        if (fileType.equals(FileType.SINGLE_FILE)) {
            return new ImageIOCoordinates(serie, channel, timePoint);
        } else return null;
    }
    
    private enum FileType {
        SINGLE_FILE;
    }
}
