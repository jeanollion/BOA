/*
 * Copyright (C) 2016 jollion
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
import core.Task;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;

/**
 *
 * @author jollion
 */
public class CountObjects {
    public static void main(String[] args) {
        String dbName = "fluo160428";
        //String dbName = "boa_fluo151127";
        MasterDAO db = new Task(dbName, null).getDB();
        /*ObjectDAO dao = db.getDao(db.getExperiment().getPositionsAsString()[0]);
        StructureObject root = dao.getRoot(0);
        StructureObject mc = root.getChildren(0).get(0);
        StructureObject bact = mc.getChildren(1).get(0);
        StructureObject mut = mc.getChildren(2).get(0);
        logger.debug("mc: {}, bact: {}, mut: {}, mut parent 1: {}, mut parent 0: {}", mc, bact, mut, mut.getParent(1), mut.getParent(0));
        //mut.getParent(1)
        */
        for (String f : db.getExperiment().getPositionsAsString()) {
            ObjectDAO dao = db.getDao(f);
            countMicrochannels(dao);
        }
    }
    private static void countMicrochannels(ObjectDAO dao) {
        logger.debug("Position: {}, mc: {}", dao.getPositionName(), dao.getTrackHeads(dao.getRoot(0), 0).size());
        
    }
}
