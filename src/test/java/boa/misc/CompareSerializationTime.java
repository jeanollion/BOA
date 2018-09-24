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
import boa.core.Task;
import boa.data_structure.dao.MasterDAO;
import boa.data_structure.dao.ObjectDAO;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import boa.utils.JSONUtils;

/**
 *
 * @author Jean Ollion
 */
public class CompareSerializationTime {
    public static void main(String[] args) {
        String dbName = "fluo151127";
        MasterDAO db = new Task(dbName).getDB();
        ObjectDAO dao  = db.getDao(db.getExperiment().getPosition(0).getName());
        List<StructureObject> obs = StructureObjectUtils.getAllObjects(dao, 1);
        testSerialization(obs, "default ser ", o->JSONUtils.serialize(o), s->JSONUtils.parse(StructureObject.class, s));
        testSerialization(obs, "JSON simple ser ", o->o.toJSONEntry().toJSONString(), s->new StructureObject(JSONUtils.parse(s)));
    }
    public static void testSerialization(List<StructureObject> objects, String serName,  Function<StructureObject, String> ser, Function<String, StructureObject> unser) {
        List<String> serList = new ArrayList<>();
        List<StructureObject> unserList = new ArrayList<>();
        long t0 = System.currentTimeMillis();
        for (StructureObject o : objects) serList.add( ser.apply(o));
        long t1 = System.currentTimeMillis();
        for (String o : serList) unserList.add(unser.apply(o));
        long t2 = System.currentTimeMillis();
        // check equality
        int count = 0;
        for (int i = 0; i<objects.size(); ++i) {
            StructureObject o = objects.get(i);
            StructureObject o2 = unserList.get(i);
            
            if (o.getRegion().getVoxels().size()!=
                    o2.getRegion().getVoxels().size()) ++count;
        }
        //for (int i = 0; i<objects.size(); ++i) logger.debug("{}>{}", objects.get(i), serList.get(0));
        logger.debug(serName+" time to Serialize: {}, unser: {}, unequals: {}", ((double)(t1-t0))/objects.size(), ((double)(t2-t1))/objects.size(), count);
    }
}
