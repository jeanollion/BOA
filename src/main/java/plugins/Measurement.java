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

import dataStructure.objects.StructureObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import measurement.MeasurementKey;

/**
 *
 * @author jollion
 */
public interface Measurement extends Plugin {
    /**
     * 
     * @return index of structure of the objects that will be provided to the method {@link Measurement#performMeasurement(dataStructure.objects.StructureObject, java.util.ArrayList) }. In case the measurement depends on several structures, it should be the index of the fisrt common parent
     */
    public int getCallStructure();
    /**
     * 
     * @return true if the measurement should be called only on track heads, false if it should be called on each timePoint
     */
    public boolean callOnlyOnTrackHeads();
    /**
     * 
     * @return list of MeasurementKeys.
     */
    public List<MeasurementKey> getMeasurementKeys();
    /**
     * 
     * @param object object (or closet parent) to perform measurement on
     * @param modifiedObjects
     */
    public void performMeasurement(StructureObject object, List<StructureObject> modifiedObjects);
}
