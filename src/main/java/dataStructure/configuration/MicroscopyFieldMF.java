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
package dataStructure.configuration;

import configuration.parameters.NumberParameter;
import configuration.parameters.PluginParameter;
import plugins.Crop;
import plugins.Rotation;

/**
 *
 * @author jollion
 */
public class MicroscopyFieldMF extends MicroscopyField {
    NumberParameter numberOfChannels = new NumberParameter("Number of Micro-Fluidic Channels", 0);
    PluginParameter preCrop = new PluginParameter("Pre-Cropping", Crop.class, true);
    PluginParameter rotation = new PluginParameter("Rotation", Rotation.class, true);
    PluginParameter crop = new PluginParameter("Pre-Cropping", Crop.class, true);
    
    @Override
    protected void initChildList() {
        super.initChildList();
        initChildren(numberOfChannels, preCrop, rotation, crop);
    }
    
}
