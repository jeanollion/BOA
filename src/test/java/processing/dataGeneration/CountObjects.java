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
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.ObjectDAO;

/**
 *
 * @author jollion
 */
public class CountObjects {
    public static void main(String[] args) {
        String dbName = "boa_fluo160428";
        //String dbName = "boa_fluo151127";
        MorphiumMasterDAO db = new MorphiumMasterDAO(dbName);
        for (String f : db.getExperiment().getPositionsAsString()) {
            ObjectDAO dao = db.getDao(f);
            countMicrochannels(dao);
        }
    }
    private static void countMicrochannels(ObjectDAO dao) {
        logger.debug("Position: {}, mc: {}", dao.getFieldName(), dao.getTrackHeads(dao.getRoot(0), 0).size());
        
    }
}
