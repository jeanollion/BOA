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
package boa.plugins.plugins.transformations;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.ChoiceParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.input_image.InputImages;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageShort;
import boa.plugins.ConfigurableTransformation;
import boa.plugins.MultichannelTransformation;
import boa.plugins.ToolTip;
import boa.utils.Utils;

/**
 *
 * @author Jean Ollion
 */
public class TypeConverter implements MultichannelTransformation, ToolTip {

    public enum METHOD {LIMIT_TO_16}
    ChoiceParameter method = new ChoiceParameter("Method", Utils.toStringArray(METHOD.values()), METHOD.LIMIT_TO_16.toString(), false).setToolTipText("<ul><li><b>"+METHOD.LIMIT_TO_16.toString()+"</b>: Only 32-bit Images are converted to 16-bits</li><</ul>");
    NumberParameter constantValue = new BoundedNumberParameter("Add value", 0, 0, 0, Short.MAX_VALUE).setToolTipText("Adds this value to all images. This is useful to avoid trimming negative during convertion from 32-bit to 8-bit or 16-bit. No check is done to enshure values will be within 16-bit range");
    ConditionalParameter cond = new ConditionalParameter(method).setActionParameters(METHOD.LIMIT_TO_16.toString(), constantValue);
    Parameter[] parameters = new Parameter[]{cond};
    
    public TypeConverter() {
    }
    @Override
    public String getToolTipText() {
        return "Converts bit-depth of all images";
    }
    public TypeConverter setLimitTo16(short addValue) {
        this.method.setSelectedItem(METHOD.LIMIT_TO_16.toString());
        constantValue.setValue(addValue);
        return this;
    }
    @Override
    public OUTPUT_SELECTION_MODE getOutputChannelSelectionMode() {
        return OUTPUT_SELECTION_MODE.ALL;
    }

    @Override
    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        switch(METHOD.valueOf(method.getSelectedItem())) {
            case LIMIT_TO_16:
            default:
                if (image instanceof ImageFloat) {
                    double add = 0.5 + constantValue.getValue().doubleValue();
                    Image output = new ImageShort(image.getName(), image);
                    for (int z = 0; z<image.sizeZ(); ++z) {
                        for (int xy = 0; xy<image.sizeXY(); ++xy) {
                            output.setPixel(xy, z, image.getPixel(xy, z)+add);
                        }
                    }
                    return output;
                } else return image;
        }
    }
    
    @Override
    public void setTestMode(boolean testMode) { }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }

}
