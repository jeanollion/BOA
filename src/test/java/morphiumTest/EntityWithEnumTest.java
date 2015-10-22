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
import de.caluga.morphium.Morphium;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import utils.MorphiumUtils;

/**
 *
 * @author jollion
 */
public class EntityWithEnumTest {
    @Test
    public void updateEnumTest() {
        Morphium m = MorphiumUtils.createMorphium("testUpdateEnum");
        m.clearCollection(EntityWithEnum.class);
        EntityWithEnum e1 = new EntityWithEnum(1);
        e1.setFlag(EntityWithEnum.Flag.flag1);
        EntityWithEnum e2 = new EntityWithEnum(2);
        m.store(e1);
        m.store(e2);
        e2.setFlag(EntityWithEnum.Flag.flag2);
        m.updateUsingFields(e2, "flag");
        
        EntityWithEnum e1Fetched = m.createQueryFor(EntityWithEnum.class).getById(e1.id);
        EntityWithEnum e2Fetched = m.createQueryFor(EntityWithEnum.class).getById(e2.id);
        assertEquals("fetched e1 flag: ", EntityWithEnum.Flag.flag1, e1Fetched.getFlag());
        assertEquals("fetched e2 flag: ", EntityWithEnum.Flag.flag2, e2Fetched.getFlag());
    }
}
