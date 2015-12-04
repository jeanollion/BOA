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
import de.caluga.morphium.annotations.Reference;
import de.caluga.morphium.annotations.Transient;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
@Embedded(polymorph=true)
public class ObjectContainerVoxelsDB extends ObjectContainer {
    protected ObjectId regionId;
    @Transient RegionVoxelsDB region;
    
    public ObjectContainerVoxelsDB(StructureObject structureObject) {
        super(structureObject);
    }

    @Override
    public void updateObject() {
        getRegion().updateObject(structureObject);
        regionId=region.getId();
    }
    
    protected RegionVoxelsDB getRegion() {
        if (region==null) {
            if (regionId!=null) {
                region = structureObject.getDAO().getRegionDAO().getObject(regionId);
                if (region==null) {
                    regionId=null;
                    region= new RegionVoxelsDB(structureObject);
                }
            } else region= new RegionVoxelsDB(structureObject);
        }
        return region;
    }

    @Override
    public void deleteObject() {
        if (regionId!=null) structureObject.getDAO().getRegionDAO().delete(regionId);
        region=null;
        regionId=null;
    }

    @Override
    public void relabelObject(int newIdx) {
    }
    
    @Override
    public Object3D getObject() {
        return new Object3D(getRegion().getVoxels(), structureObject.getIdx() + 1, bounds, structureObject.getScaleXY(), structureObject.getScaleZ());
    }
    
}
