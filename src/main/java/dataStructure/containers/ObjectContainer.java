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
package dataStructure.containers;

import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.annotations.Embedded;
import de.caluga.morphium.annotations.Transient;
import image.BoundingBox;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 *
 * @author jollion
 */

@Embedded(polymorph=true)
public abstract class ObjectContainer {
    @Transient public static int MAX_VOX_3D = 1200000; //(1 vox = 12B)
    @Transient public static int MAX_VOX_2D = 1900000; //(1 vox =8B)
    @Transient public static final int MAX_VOX_3D_EMB = 20;
    @Transient public static final int MAX_VOX_2D_EMB = 30;
    @Transient protected StructureObject structureObject;
    BoundingBox bounds;
    
    public ObjectContainer(StructureObject structureObject) {
        this.structureObject=structureObject;
        this.bounds=structureObject.getBounds();
    }
    public void setStructureObject(StructureObject structureObject) {
        this.structureObject=structureObject;
    }
    protected float getScaleXY() {return structureObject.getMicroscopyField().getScaleXY();}
    protected float getScaleZ() {return structureObject.getMicroscopyField().getScaleZ();}
    public abstract Object3D getObject();
    public abstract void updateObject();
    public abstract void deleteObject();
    public abstract void relabelObject(int newIdx);
    public void initFromJSON(JSONObject json) {
        JSONArray bds =  (JSONArray)json.get("bounds");
        this.bounds=new BoundingBox();
        bounds.setxMin((int)bds.get(0));
        bounds.setxMax((int)bds.get(1));
        bounds.setyMin((int)bds.get(2));
        bounds.setyMax((int)bds.get(3));
        if (bds.size()>=6) {
            bounds.setzMin((int)bds.get(4));
            bounds.setzMax((int)bds.get(5));
        }
    }
    public JSONObject toJSON() {
        JSONArray bds =  new JSONArray();
        bds.add(bounds.getxMin());
        bds.add(bounds.getxMax());
        bds.add(bounds.getyMin());
        bds.add(bounds.getyMax());
        if (bounds.getSizeZ()>1 || bounds.getzMin()>0) {
            bds.add(bounds.getzMin());
            bds.add(bounds.getzMax());
        }
        JSONObject res = new JSONObject();
        res.put("bounds", bds);
        return res;
    }
    protected ObjectContainer() {}
    public static ObjectContainer createFromJSON(StructureObject o, JSONObject json) {
        ObjectContainer res;
        if (json.containsKey("x")) res = new ObjectContainerVoxels();
        else if (json.containsKey("roi")||json.containsKey("roiZ")) res = new ObjectContainerIjRoi();
        else res = new ObjectContainerBlankMask();
        res.setStructureObject(o);
        res.initFromJSON(json);
        return res;
    }
}
