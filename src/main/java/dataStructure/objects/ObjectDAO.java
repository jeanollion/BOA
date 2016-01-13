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

import dataStructure.configuration.Experiment;
import java.util.ArrayList;
import java.util.List;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public interface ObjectDAO {
    public Experiment getExperiment();
    public String getFieldName();
    public void clearCache();
    public ArrayList<StructureObject> getChildren(StructureObject parent, int structureIdx); // needs indicies: structureIdx & parent
    public void deleteChildren(final StructureObject parent, int structureIdx);
    public void deleteObjectsByStructureIdx(int... structures);
    public void deleteAllObjects();
    public void delete(StructureObject o, boolean deleteChildren);
    revoir les fonctions deletes avec la gestions des enfant directs et indirects.. la fonction delete doit elle appeller deleteChildren?
    public void delete(List<StructureObject> list, final boolean deleteChildren);
    public void store(StructureObject object, boolean updateTrackAttributes);
    public void store(final List<StructureObject> objects, final boolean updateTrackAttributes);
    
    public ArrayList<StructureObject> getRoots(); // needs indicies: structureIdx
    public StructureObject getRoot(int timePoint);
    
    public ArrayList<StructureObject> getTrack(StructureObject trackHead);
    public ArrayList<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx);
    
    public void upsertMeasurements(List<StructureObject> objects);
    public void upsertMeasurement(StructureObject o);
    public List<Measurements> getMeasurements(int structureIdx, String... measurements);
}
