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

/**
 *
 * @author jollion
 */
public interface Transformation extends ImageProcessingPlugin {
    public static enum SelectionMode{Single, Multiple, All, None};
    /**
     * This method compute configuration data necesary for {@link Transformation#applyTransformation(image.Image)} method; data is retrieved by the {@link Transformation#getConfigurationData() } method; in this metohd the objects should not be modified but created de novo.
     * @param inputImages 
     */
    public void computeConfigurationData(InputImages inputImages);
    public Image applyTransformation(Image input);
    /**
     * 
     * @return an array of objects that store parameters computed after the {@link Transformation#computeConfigurationData(dataStructure.containers.InputImages) } method and that will be used for the {@link Transformation#applyTransformation(image.Image) } method. The objects contained in the array can be modified by the program in order to retrieve de configuration data. The content of these objects should never be modified 
     */
    public Object[] getConfigurationData();
    public SelectionMode getChannelSelectionMode();
}
