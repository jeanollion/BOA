/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.image;

public interface ImageProperties<T extends ImageProperties> {
    public String getName();
    public int getSizeX();
    public int getSizeY();
    public int getSizeZ();
    public int getSizeXY();
    public int getSizeXYZ();
    public int getOffsetX();
    public int getOffsetXY();
    public int getOffsetY();
    public int getOffsetZ();
    public float getScaleXY();
    public float getScaleZ();
    public T setCalibration(ImageProperties properties);
    public T setCalibration(float scaleXY, float scaleZ);
    public boolean contains(int x, int y, int z);
    public boolean containsWithOffset(int x, int y, int z);
    public BoundingBox getBoundingBox();
    public boolean sameDimensions(ImageProperties image);
    public ImageProperties getProperties();
    public T addOffset(BoundingBox bounds);
    public T addOffset(ImageProperties bounds);
}
