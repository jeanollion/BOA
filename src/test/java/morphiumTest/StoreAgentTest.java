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
package morphiumTest;

import static TestUtils.Utils.logger;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.Object3D;
import dataStructure.objects.StructureObject;
import dataStructure.objects.Voxel;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.Test;

/**
 *
 * @author jollion
 */
public class StoreAgentTest {
    //@Test
    public void storeAgentTest() {
        try {
            MorphiumMasterDAO db = new MorphiumMasterDAO("storeAgentTest");
            
            db.getDao().storeLater(getList(null, 50), true, false);
            db.getDao().storeLater(getList(null, 500), true, false);
            db.getDao().storeLater(getList(null, 500), true, false);
            Thread.sleep(5000);
            db.getDao().storeLater(getList(null, 50), true, false);
            db.getDao().storeLater(getList(null, 500), true, false);
            Thread.sleep(5000);
            
        } catch (InterruptedException ex) {
            logger.error("thread sleep", ex);
        }
    }
    private static List<StructureObject> getList(StructureObject parent, int n) {
        List<StructureObject> list = new ArrayList<StructureObject>(n);
        for (int i = 0; i<n; ++i) list.add(new StructureObject("test", i, 0, i%10, new Object3D(getVoxelList(100), 1, 1, 1), parent));
        return list;
    }
    private static ArrayList<Voxel> getVoxelList(int n ) {
        ArrayList<Voxel> vox = new ArrayList<Voxel>(10);
        for (int i = 0; i<n; ++i) vox.add(new Voxel(n, n%2, 0));
        return vox;
    }
}
