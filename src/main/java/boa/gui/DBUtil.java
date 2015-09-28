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
package boa.gui;

import static boa.gui.GUI.logger;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoIterable;
import java.util.ArrayList;

/**
 *
 * @author jollion
 */
public class DBUtil {
    public static ArrayList<String> getDBNames(String hostName) {
        try {
            MongoClient c = new MongoClient(hostName);
            MongoIterable<String> dbs = c.listDatabaseNames();
            ArrayList<String> res = new ArrayList<String>();
            for (String s : dbs) res.add(s);
            return res;
        } catch (Exception e) {
            logger.error("DB connection error", e);
        }
        return null;
    }
}
