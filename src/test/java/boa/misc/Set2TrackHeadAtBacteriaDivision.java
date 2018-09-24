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
package boa.misc;

import static boa.test_utils.TestUtils.logger;
import boa.core.Processor;
import boa.core.Task;
import boa.data_structure.dao.DBMapObjectDAO;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import boa.plugins.PluginFactory;
import boa.utils.ArrayUtil;
import boa.utils.Utils;

/**
 *
 * @author Jean Ollion
 */
public class Set2TrackHeadAtBacteriaDivision {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        String sourceDBName = "fluo160501";
        String[] dbNames = new String[]{"fluo160501", "fluo160428", "fluo151127"};
        //int[] positions = new int[]{5};
        int[] positions = null;
        //copyObjects(sourceDBName, dbName, positions, 0, 1);
        for (String dbName : dbNames) fixTrackHead(dbName, positions);
    }
    private static void copyObjects(String fromXP, String toXP, int[] positions, int... structureIdx) { // TODO : bug!!
        MasterDAO dbSource = new Task(fromXP).getDB();
        MasterDAO dbTarget = new Task(toXP).getDB();
        for (int pIdx : positions) {
            logger.debug("position: {}: {}->{}", pIdx, dbSource.getExperiment().getPosition(pIdx).getName(), dbTarget.getExperiment().getPosition(pIdx).getName());
            ObjectDAO daoSource = dbSource.getDao(dbSource.getExperiment().getPosition(pIdx).getName());
            ObjectDAO daoTarget = dbTarget.getDao(dbTarget.getExperiment().getPosition(pIdx).getName());
            daoTarget.deleteAllObjects();
            List<StructureObject> roots = Processor.getOrCreateRootTrack(daoSource);
            daoTarget.store(roots);
            logger.debug("pos : {}, roots: {}", pIdx, roots.size());
            
                Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
                logger.debug("all tracks: {}", allTracks.size());
                Collection<StructureObject> objects = Utils.flattenMap(allTracks);
                //Collection<StructureObject> objects = StructureObjectUtils.getAllObjects(daoSource, sIdx);
                logger.debug("Structure: {}, objects: {}", 0, objects.size());
                daoTarget.store(objects);
            
            ((DBMapObjectDAO)daoTarget).compactDBs(true);
            daoTarget.clearCache();
        }
        
    }
    private static void fixTrackHead(String xp, int... pIdces) {
        MasterDAO db = new Task(xp).getDB();
        if (pIdces==null || pIdces.length==0) pIdces = ArrayUtil.generateIntegerArray(db.getExperiment().getPositionCount());
        for (int pIdx : pIdces) {
            ObjectDAO dao = db.getDao(db.getExperiment().getPosition(pIdx).getName());
            List<StructureObject> roots = dao.getRoots();
            StructureObjectUtils.setAllChildren(roots, 1);
            List<StructureObject> bucket = new ArrayList<>(4);
            Set<StructureObject> modified = new HashSet<>();
            Map<StructureObject, List<StructureObject>> mcs = StructureObjectUtils.getAllTracks(roots, 0);
            for (List<StructureObject> mcTrack : mcs.values()) {
                ListIterator<StructureObject> it = mcTrack.listIterator(mcTrack.size()-2);
                while (it.hasPrevious()) {
                    StructureObject p = it.previous();
                    for (StructureObject b  : p.getChildObjects(1)) {
                        StructureObjectUtils.getDaugtherObjectsAtNextFrame(b, bucket);
                        if (bucket.size()>1) {
                            Collections.sort(bucket);
                            for (StructureObject bb : bucket) {
                                bb.setTrackHead(bb, true, true, modified);
                                b.setTrackLinks(bb, true, false);
                                modified.add(b);
                            }
                        }
                    }
                }
            }
            logger.debug("modified objects: {}", modified.size());
            dao.store(modified);
        }
    }
}
