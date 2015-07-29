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
package dataStructure;

import dataStructure.containers.MultipleImageContainer;
import image.BlankMask;
import image.BoundingBox;
import image.Image;
import image.ImageProperties;

/**
 *
 * @author jollion
 */
public class MultipleImageContainerDummy extends MultipleImageContainer {
    ImageProperties properties;
    public MultipleImageContainerDummy(ImageProperties properties) {
        this.properties=properties;
    }
    
    @Override
    public Image getImage(int timePoint, int channel) {
        return new BlankMask("@t:"+timePoint+" @c:"+channel, properties);
    }

    @Override
    public Image getImage(int timePoint, int channel, BoundingBox bounds) {
        return bounds.getImageProperties("@t:"+timePoint+" @c:"+channel, properties.getScaleXY(), properties.getScaleZ());
    }
    
}
