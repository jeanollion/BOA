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
import dataStructure.configuration.Experiment;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author jollion
 */
public class ResetXPParameters {
    public static void main(String[] args) {
        String[] dbs = new String[]{"boa_phase150616wt", "boa_phase141107wt", "boa_phase150324mutH"};
        for (String db:dbs) resetParameters(db, true, true);
        logger.debug("done!");
    }
    public static void resetParameters(String dbName, boolean processing, boolean measurements) {
        MasterDAO db = new MorphiumMasterDAO(dbName);
        GenerateTestXP.setParametersTrans(db.getExperiment(), processing, measurements);
        db.updateExperiment();
    }
}
