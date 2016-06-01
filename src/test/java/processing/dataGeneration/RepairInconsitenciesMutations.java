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
import boa.gui.GUI;
import boa.gui.ManualCorrection;
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.Selection;
import dataStructure.objects.StructureObject;
import dataStructure.objects.StructureObjectUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class RepairInconsitenciesMutations {
    static int structureIdx = 2;
    public static void main(String[] args) {
        //String dbName = "boa_fluo160501";
        String dbName = "boa_fluo160428";
        MasterDAO mDAO = new MorphiumMasterDAO(dbName);
        //ManualCorrection.repairLinksForField(mDAO, mDAO.getExperiment().getFieldsAsString()[0], structureIdx);
        ManualCorrection.repairLinksForField(mDAO, mDAO.getExperiment().getFieldsAsString()[1], structureIdx);
    }
    
}
