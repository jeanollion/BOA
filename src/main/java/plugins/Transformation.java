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

import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectPreProcessing;
import image.Image;

/**
 *
 * @author jollion
 */
public interface Transformation extends ImageProcessingPlugin {
    public void computeParameters(int structureIdx, StructureObjectPreProcessing structureObject);
    public Image applyTransformation(Image input);
    public boolean isTimeDependent();
    /**
     * 
     * @return an array of objects that store parameters computed after the {@link Transformation#computeParameters(int, dataStructure.objects.StructureObjectPreFilter)} method and that will be used for the {@link Transformation#applyTransformation(image.Image) } method
     */
    public Object[] getConfigurationParameters();

}
