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
package plugins.plugins.thresholders;

import configuration.parameters.Parameter;
import configuration.parameters.PluginParameter;
import plugins.PreProcessor;
import plugins.Thresholder;

/**
 *
 * @author jollion
 */
public class DummyThresholder1 implements Thresholder {
    public Parameter[] getParameters() {
        return new Parameter[]{new PluginParameter("PreProcessing", PreProcessor.class)};
    }
}
