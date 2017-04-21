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
package core.generateXP;

import core.Task;
import core.generateXP.GenerateXP;
import dataStructure.objects.MasterDAO;
import dataStructure.objects.MorphiumMasterDAO;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author jollion
 */
public class ResetXPParameters {
    public static final Logger logger = LoggerFactory.getLogger(ResetXPParameters.class);
    public static void main(String[] args) {
        //String[] dbs = new String[]{"boa_phase150616wt", "boa_phase141107wt", "boa_phase150324mutH"};
        //for (String db:dbs) resetParametersTrans(db, true, true);
        //String[] dbs = new String[]{"boa_phase150616wtBis", "boa_phase141107wtBis"};
        //String[] dbs = new String[]{"boa_fluo151127"};
        //for (String db:dbs) resetParametersFluo(db, true, true);
        
        resetPreProcessingFluo("boa_fluo151127", true, 4);
        logger.debug("done!");
    }
    public static void resetParametersTrans(String dbName, boolean processing, boolean measurements) {
        MasterDAO db = new Task(dbName).getDB();
        if (db==null) {
            logger.error("DB {} not found", dbName);
            return;
        }
        GenerateXP.setParametersTrans(db.getExperiment(), processing, measurements);
        db.updateExperiment();
    }
    public static void resetParametersFluo(String dbName, boolean processing, boolean measurements) {
        MasterDAO db = new Task(dbName).getDB();
        if (db==null) {
            logger.error("DB {} not found", dbName);
            return;
        }
        GenerateXP.setParametersFluo(db.getExperiment(), processing, measurements);
        db.updateExperiment();
    }
    public static void resetPreProcessingFluo(String dbName, boolean flip, int... positionIndices) {
        resetPreProcessingFluo(dbName, flip, 0, 0, Double.NaN, positionIndices);
    }
    public static void resetPreProcessingFluo(String dbName, boolean flip, int trimeStart, int trimEnd, double scaleXY, int... positionIndices) {
        MasterDAO db = new Task(dbName).getDB();
        if (db==null) {
            logger.error("DB {} not found", dbName);
            return;
        }
        if (positionIndices.length==0) GenerateXP.setPreprocessingFluo(db.getExperiment().getPreProcessingTemplate(), flip, trimeStart, trimEnd, scaleXY, null);
        else {
            for (int i : positionIndices) GenerateXP.setPreprocessingFluo(db.getExperiment().getPosition(i).getPreProcessingChain(), flip, trimeStart, trimEnd, scaleXY, null);
        }
        db.updateExperiment();
    }
    
}
