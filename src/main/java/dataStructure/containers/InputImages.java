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

import image.Image;

/**
 *
 * @author jollion
 */
public class InputImages {
    InputImage[][] imageTC;
    int[] timePoints, channels;
    int currentTimePointIdx, currentChannelIdx;

    public InputImages(InputImage[][] imageTC, int[] timePoints, int[] channels) {
        this.imageTC = imageTC;
        this.timePoints = timePoints;
        this.channels = channels;
    }

    public InputImages(InputImage[][] imageTC, int[] timePoints, int[] channels, int currentTimePointIdx, int currentChannelIdx) {
        this.imageTC = imageTC;
        this.timePoints = timePoints;
        this.channels = channels;
        this.currentTimePointIdx = currentTimePointIdx;
        this.currentChannelIdx = currentChannelIdx;
    }
    
    public int getCurrentTimePoint() {return timePoints[currentTimePointIdx];}
    public int getCurrentChannel() {return channels[currentChannelIdx];}
    public Image getCurrentImage() {
        // TODO check memory ici
        return imageTC[getCurrentTimePoint()][getCurrentChannel()].getImage();
    }
    public boolean nextChannel() {
        if (currentChannelIdx<(channels.length-1)) {
            ++currentChannelIdx;
            return true;
        } else return false;
    }
    public boolean nextTimePoint() {
        if (currentTimePointIdx<(timePoints.length-1)) {
            ++currentTimePointIdx;
            return true;
        } else return false;
    }
    public boolean previousChannel() {
        if (currentChannelIdx>0) {
            --currentChannelIdx;
            return true;
        } else return false;
    }
    public boolean previousTimePoint() {
        if (currentTimePointIdx>0) {
            --currentTimePointIdx;
            return true;
        } else return false;
    }
}
