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
package boa.plugins;

import boa.data_structure.input_image.InputImages;
import boa.image.Image;
import java.util.ArrayList;
import java.util.List;

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
    public SelectionMode getOutputChannelSelectionMode();
    public void setTestMode(boolean testMode);
}
