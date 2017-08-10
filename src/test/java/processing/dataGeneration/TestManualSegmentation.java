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

import core.Task;
import dataStructure.objects.MasterDAO;
import plugins.ManualSegmenter;
import plugins.PluginFactory;

/**
 *
 * @author jollion
 */
public class TestManualSegmentation {
    public static void main(String[] args) {
        PluginFactory.findPlugins("plugins.plugins");
        String dbName= "fluo151127";
        int field = 0;
        testManualSegmentation(dbName);
    }
    public static void testManualSegmentation(String dbName) {
        MasterDAO db = new Task(dbName).getDB();
        ManualSegmenter m = db.getExperiment().getStructure(2).getManualSegmenter();
        m.manualSegment(null, null, null, 2, null);
    }
}
