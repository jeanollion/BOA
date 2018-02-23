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
package boa.image.io;

import boa.image.BoundingBox;
import boa.image.SimpleBoundingBox;



/**
 *
 * @author jollion
 */
public class ImageIOCoordinates {
    int serie, channel, timePoint;
    BoundingBox bounds;

    public ImageIOCoordinates(int serie, int channel, int timePoint) {
        this.serie = serie;
        this.channel = channel;
        this.timePoint = timePoint;
    }
    
    public ImageIOCoordinates(int serie, int channel, int timePoint, BoundingBox bounds) {
        this.serie = serie;
        this.channel = channel;
        this.timePoint = timePoint;
        this.bounds = bounds;
    }
    
    public ImageIOCoordinates(BoundingBox bounds) {
        this.bounds=bounds;
    }
    
    public ImageIOCoordinates() {}

    public int getSerie() {
        return serie;
    }

    public int getChannel() {
        return channel;
    }

    public int getTimePoint() {
        return timePoint;
    }

    public BoundingBox getBounds() {
        return bounds;
    }
    
    public void setBounds(BoundingBox bounds) {
        this.bounds=bounds;
    }
    
    public ImageIOCoordinates duplicate() {
        return new ImageIOCoordinates(serie, channel, timePoint, (bounds!=null)?new SimpleBoundingBox(bounds):null);
    }
    
}
