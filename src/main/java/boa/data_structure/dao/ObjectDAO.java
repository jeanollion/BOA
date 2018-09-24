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
package boa.data_structure.dao;

import boa.core.ProgressCallback;
import boa.configuration.experiment.Experiment;
import boa.data_structure.Measurements;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 * @author Jean Ollion
 */
public interface ObjectDAO {
    public MasterDAO getMasterDAO();
    public void applyOnAllOpenedObjects(Consumer<StructureObject> function);
    public Experiment getExperiment();
    public String getPositionName();
    public void clearCache();
    public boolean isReadOnly();
    StructureObject getById(String parentTrackHeadId, int structureIdx, int frame, String id);
    public List<StructureObject> getChildren(StructureObject parent, int structureIdx); // needs indicies: structureIdx & parent
    /**
     * Sets children for each parent in parent Track
     * @param parentTrack object with same trackHead id
     * @param structureIdx direct child of parent
     */
    public void setAllChildren(List<StructureObject> parentTrack, int structureIdx);
    /**
     * Deletes the children of {@param parent} of structure {@param structureIdx}
     * @param parent
     * @param structureIdx 
     */
    public void deleteChildren(final StructureObject parent, int structureIdx);
    public void deleteChildren(Collection<StructureObject> parents, int structureIdx);
    /**
     * Deletes all objects from the given structure index  plus all objects from direct or indirect children structures
     * @param structures 
     */
    public void deleteObjectsByStructureIdx(int... structures);
    public void deleteAllObjects();
    /**
     * 
     * @param o object to delete
     * @param deleteChildren if true, deletes all direct or indirect chilren
     */
    public void delete(StructureObject o, boolean deleteChildren, boolean deleteFromParent, boolean relabelParent);
    public void delete(Collection<StructureObject> list, boolean deleteChildren, boolean deleteFromParent, boolean relabelParent);
    //revoir les fonctions deletes avec la gestions des enfant directs et indirects.. la fonction delete doit elle appeller deleteChildren?
    public void store(StructureObject object);
    public void store(final Collection<StructureObject> objects);
    
    public List<StructureObject> getRoots();
    public void setRoots(List<StructureObject> roots);
    public StructureObject getRoot(int timePoint);
    
    public List<StructureObject> getTrack(StructureObject trackHead);
    public List<StructureObject> getTrackHeads(StructureObject parentTrack, int structureIdx);
    
    public void upsertMeasurements(Collection<StructureObject> objects);
    public void upsertMeasurement(StructureObject o);
    public void retrieveMeasurements(int... structureIdx);
    public Measurements getMeasurements(StructureObject o);
    public List<Measurements> getMeasurements(int structureIdx, String... measurements);
    public void deleteAllMeasurements();
    
    public static boolean sameContent(ObjectDAO dao1, ObjectDAO dao2, ProgressCallback pcb) {
        List<StructureObject> roots1 = dao1.getRoots();
        List<StructureObject> roots2 = dao2.getRoots();
        if (!roots1.equals(roots2)) {
            pcb.log("positions:"+dao1.getPositionName()+" differs in roots");
            return false;
        }
        for (int sIdx =0; sIdx< dao1.getExperiment().getStructureCount() ; sIdx++) {
            Set<StructureObject> allObjects1 = new HashSet<>(StructureObjectUtils.getAllObjects(dao1, sIdx));
            Set<StructureObject> allObjects2 = new HashSet<>(StructureObjectUtils.getAllObjects(dao2, sIdx));
            if (!allObjects1.equals(allObjects2)) {
                pcb.log("positions:"+dao1.getPositionName()+" differs @ structure: "+sIdx + "#"+allObjects1.size()+" vs #"+allObjects2.size());
                return false;
            }
            // deep equals
            Map<String, StructureObject> allObjects2Map = allObjects2.stream().collect(Collectors.toMap(StructureObject::getId, Function.identity()));
            for (StructureObject o1 : allObjects1) {
                StructureObject o2  = allObjects2Map.get(o1.getId());
                if (!o1.toJSONEntry().toJSONString().equals(o2.toJSONEntry().toJSONString())) {
                    pcb.log("positions:"+dao1.getPositionName()+" differs @ object: "+o1.toStringShort());
                    return false;
                }
            }
        }
        return true;
    }
}
