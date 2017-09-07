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
package TestUtils;

import static TestUtils.Utils.logger;
import core.Task;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.Selection;
import utils.JSONUtils;

/**
 *
 * @author jollion
 */
public class TestSelSerialization {
    public static void main(String[] args) {
        String xp = "fluo160428";
        MasterDAO db = new Task(xp).getDB();
        Selection s = db.getSelectionDAO().getSelections().get(1);
        String ser = JSONUtils.serialize(s);
        Selection s2 = JSONUtils.parse(Selection.class, ser);
        logger.debug("positions: {}", s.getAllPositions().equals(s2.getAllPositions()));
        for (String position : s.getAllPositions()) {
            logger.debug("position: {} elements: {} ({})", position,  s.getElementStrings(position).equals(s2.getElementStrings(position)), s2.getElementStrings(position).size());
        }
        logger.debug("name: {} ({})", s.getName().equals(s2.getName()), s2.getName());
        logger.debug("color: {} ({})", s.getColor(false).equals(s2.getColor(false)), s2.getColor(false));
        logger.debug("disp obj: {} ({})", s.isDisplayingObjects() == s2.isDisplayingObjects(), s2.isDisplayingObjects());
        logger.debug("disp tr: {} ({})", s.isDisplayingTracks() == s2.isDisplayingTracks(), s2.isDisplayingTracks());
        logger.debug("high tr: {} ({})", s.isHighlightingTracks()== s2.isHighlightingTracks(), s2.isHighlightingTracks());
    }
}
