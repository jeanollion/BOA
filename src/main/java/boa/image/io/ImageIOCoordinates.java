/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.image.io;

import boa.image.BoundingBox;
import boa.image.SimpleBoundingBox;



/**
 *
 * @author Jean Ollion
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
