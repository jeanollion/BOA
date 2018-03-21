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
package boa.plugins.plugins.transformations;

import boa.gui.imageInteraction.IJImageDisplayer;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.data_structure.StructureObjectPreProcessing;
import boa.image.Image;
import java.util.ArrayList;
import boa.image.processing.ImageTransformation;
import boa.image.processing.ImageTransformation.Axis;
import boa.plugins.MultichannelTransformation;

/**
 *
 * @author jollion
 */
public class Flip implements MultichannelTransformation {
    
    ChoiceParameter direction = new ChoiceParameter("Flip Axis Direction", new String[]{Axis.X.toString(), Axis.Y.toString(), Axis.Z.toString()}, Axis.Y.toString(), false);
    Parameter[] p = new Parameter[]{direction};
    public Flip() {}
    
    public Flip(Axis axis) {
        direction.setSelectedItem(axis.toString());
    }
    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        Axis axis = Axis.valueOf(direction.getSelectedItem());
        //logger.debug("performing flip: axis: {}, channel: {}, timePoint: {}", axis, channelIdx, timePoint);
        ImageTransformation.flip(image, axis);
        return image;
    }

    @Override
    public Parameter[] getParameters() {
        return p;
    }
    
    @Override
    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }

    boolean testMode;
    @Override public void setTestMode(boolean testMode) {this.testMode=testMode;}
}
