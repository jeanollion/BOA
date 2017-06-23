/*
 * Copyright (C) 2017 jollion
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
package processing.dataGeneration;

import static TestUtils.Utils.logger;
import core.Processor;
import core.Task;
import dataStructure.objects.DBMapObjectDAO;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import plugins.PluginFactory;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class Set2TrackHeadAtBacteriaDivision {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        String sourceDBName = "fluo160501";
        String dbName = "fluo160501_uncorr";
        int[] positions = new int[]{0, 1, 3};
        //copyObjects(sourceDBName, dbName, positions, 0, 1);
        for (int pIdx : positions) fixTrackHead(dbName, pIdx);
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
            daoTarget.store(roots, false);
            logger.debug("pos : {}, roots: {}", pIdx, roots.size());
            
                Map<StructureObject, List<StructureObject>> allTracks = StructureObjectUtils.getAllTracks(roots, 0);
                logger.debug("all tracks: {}", allTracks.size());
                Collection<StructureObject> objects = Utils.flattenMap(allTracks);
                //Collection<StructureObject> objects = StructureObjectUtils.getAllObjects(daoSource, sIdx);
                logger.debug("Structure: {}, objects: {}", 0, objects.size());
                daoTarget.store(objects, false);
            
            ((DBMapObjectDAO)daoTarget).compactDBs(true);
            daoTarget.clearCache();
        }
        
    }
    private static void fixTrackHead(String xp, int pIdx) {
        MasterDAO db = new Task(xp).getDB();
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
        dao.store(modified, true);
    }
}