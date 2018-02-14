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
package boa.data_structure.region_container;

import boa.data_structure.Region;
import boa.data_structure.StructureObject;
import boa.image.BoundingBox;
import java.util.Map;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author jollion
 */

public abstract class ObjectContainer {
    public static final int MAX_VOX_3D = 20;
    public static final int MAX_VOX_2D = 30;
    protected transient StructureObject structureObject;
    BoundingBox bounds;
    boolean is2D;
    public ObjectContainer(StructureObject structureObject) {
        this.structureObject=structureObject;
        this.bounds=structureObject.getBounds();
    }
    public void setStructureObject(StructureObject structureObject) {
        this.structureObject=structureObject;
    }
    protected float getScaleXY() {return structureObject.getMicroscopyField().getScaleXY();}
    protected float getScaleZ() {return structureObject.getMicroscopyField().getScaleZ();}
    public boolean is2D() {return is2D;}
    public abstract Region getObject();
    public void updateObject() {
        is2D = structureObject.getObject().is2D();
        bounds = structureObject.getObject().getBounds();
    }
    public abstract void deleteObject();
    public abstract void relabelObject(int newIdx);
    public void initFromJSON(Map<String, Object> json) {
        JSONArray bds =  (JSONArray)json.get("bounds");
        this.bounds=new BoundingBox();
        this.bounds.initFromJSONEntry(bds);
        if (json.containsKey("is2D")) is2D = (Boolean)json.get("is2D");
        else is2D = true; // for retrocompatibility. do not call to structure object's method at it may not be fully initiated and may not have access to experiment
    }
    public JSONObject toJSON() {
        JSONObject res = new JSONObject();
        res.put("bounds", bounds.toJSONEntry());
        res.put("is2D", is2D);
        return res;
    }
    protected ObjectContainer() {}
    public static ObjectContainer createFromMap(StructureObject o, Map json) {
        ObjectContainer res;
        if (json.containsKey("x")) res = new ObjectContainerVoxels();
        else if (json.containsKey("roi")||json.containsKey("roiZ")) res = new ObjectContainerIjRoi();
        else res = new ObjectContainerBlankMask();
        res.setStructureObject(o);
        res.initFromJSON(json);
        return res;
    }
}
