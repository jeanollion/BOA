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

import boa.image.BoundingBox.LoopFunction;

public interface ImageMask<I extends ImageMask> extends ImageProperties<I> {

    public boolean insideMask(int x, int y, int z);
    public boolean insideMask(int xy, int z);
    public boolean insideMaskWithOffset(int x, int y, int z);
    public boolean insideMaskWithOffset(int xy, int z);
    public int count();
    public static void loop(ImageMask mask, LoopFunction function) {
        if (function instanceof BoundingBox.LoopFunction2) ((BoundingBox.LoopFunction2)function).setUp();
        mask.getBoundingBox().translateToOrigin().loop((x, y, z)-> {if (mask.insideMask(x, y, z)) function.loop(x, y, z);});
        if (function instanceof BoundingBox.LoopFunction2) ((BoundingBox.LoopFunction2)function).tearDown();
    }
    public static void loopWithOffset(ImageMask mask, LoopFunction function) {
        if (function instanceof BoundingBox.LoopFunction2) ((BoundingBox.LoopFunction2)function).setUp();
        mask.getBoundingBox().loop((x, y, z)-> {if (mask.insideMaskWithOffset(x, y, z)) function.loop(x, y, z);});
        if (function instanceof BoundingBox.LoopFunction2) ((BoundingBox.LoopFunction2)function).tearDown();
    }
    public ImageMask duplicateMask();
}
