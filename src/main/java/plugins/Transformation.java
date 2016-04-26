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
package plugins;

import dataStructure.containers.InputImages;
import image.Image;
import java.util.ArrayList;

/**
 *
 * @author jollion
 */
public interface Transformation extends ImageProcessingPlugin {
    public static enum SelectionMode{SAME, SINGLE, MULTIPLE, ALL};
    /**
     * This method compute configuration data necessary for {@link Transformation#applyTransformation(image.Image)} method; data is retrieved by the {@link Transformation#getConfigurationData() } method; in this metohd the objects should not be modified but created de novo.
     * @param channelIdx
     * @param inputImages 
     */
    public void computeConfigurationData(int channelIdx, InputImages inputImages);
    public boolean isConfigured(int totalChannelNumner, int totalTimePointNumber);
    public Image applyTransformation(int channelIdx, int timePoint, Image image);
    /**
     * 
     * @return an ArrayList of objects that store parameters computed after the {@link Transformation#computeConfigurationData(dataStructure.containers.InputImages) } method and that will be used for the {@link Transformation#applyTransformation(image.Image) } method. If there are some parameters to be stored, the arraylist should never be null. The objects contained in the arrayList can be modified by the program in order to retrieve de configuration data. The content of these objects should be of type: Number, primitive types, Strings or ArrayList of Number, primitive types, Strings or ArrayList 
     */
    public ArrayList getConfigurationData();
    public SelectionMode getOutputChannelSelectionMode();
}
