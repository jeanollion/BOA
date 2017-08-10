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
package dataStructure.objects;

import static core.Processor.logger;
import de.caluga.morphium.annotations.Entity;
import de.caluga.morphium.annotations.Id;
import de.caluga.morphium.annotations.Index;
import de.caluga.morphium.annotations.Transient;
import de.caluga.morphium.annotations.lifecycle.Lifecycle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import org.bson.types.ObjectId;
import org.json.simple.JSONObject;
import utils.JSONUtils;
import utils.Utils;

/**
 *
 * @author jollion
 */

@Entity
@Index(value={"structure_idx"})
public class MeasurementsLegacy {
    protected @Id ObjectId id;
    @Transient protected String positionName;
    protected int timePoint, structureIdx;
    protected double calibratedTimePoint;
    boolean isTrackHead;
    protected int[] indices;
    protected HashMap<String, Object> values;
    @Transient boolean modifications=false;
    final public static String NA_STRING = "NA";
    public MeasurementsLegacy(StructureObject o) {
        this.id=new ObjectId(o.id);
        this.calibratedTimePoint=o.getCalibratedTimePoint();
        this.positionName=o.getPositionName();
        this.timePoint=o.getFrame();
        this.structureIdx=o.getStructureIdx();
        this.isTrackHead=o.isTrackHead;
        this.values=new HashMap<>();
    }
    public Measurements getMeasurements() {
        JSONObject obj1=new JSONObject();
        obj1.put("id", id.toHexString());
        obj1.put("frame", timePoint);
        obj1.put("sIdx", structureIdx);
        obj1.put("timePointCal", calibratedTimePoint);
        obj1.put("isTh", isTrackHead);
        obj1.put("indices", JSONUtils.toJSONArray(indices));
        obj1.put("values", JSONUtils.toJSONObject(values));
        return new Measurements(obj1);
    }
}
