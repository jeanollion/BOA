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
package boa.core.generateXP;

import static boa.core.generateXP.GenerateXP.generateXPFluo;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.PreProcessingChain;
import java.io.File;
import java.util.ArrayList;
import boa.utils.FileIO;
import boa.utils.ImportExportJSON;
import boa.utils.JSONUtils;

/**
 *
 * @author jollion
 */
public class GenerateXPConfig {
    public static void main(String[] args) {
        //String path = "/home/jollion/Fiji.app/plugins/BOA"; // portable
        String path = "/data/Images/Fiji.app/plugins/BOA"; // LJP
        Experiment xpFluo = generateXPFluo("MotherMachineMutation", null, true, 0, 0, Double.NaN, null);
        exportXP(path, xpFluo, false);
        
        Experiment xpTrans = GenerateXP.generateXPTrans("MotherMachinePhaseContrast", null, true, 0, 0, Double.NaN);
        exportXP(path, xpTrans, false);
        
        Experiment xpTransFluo = GenerateXP.generateXPFluo("MotherMachinePhaseContrastAndMutations", null, true, 0, 0, Double.NaN, null);
        GenerateXP.setParametersPhase(xpTransFluo, true, false);
        PreProcessingChain ps = xpTransFluo.getPreProcessingTemplate();
        ps.removeAllTransformations();
        GenerateXP.setPreprocessingTransAndMut(ps, 0, 0, Double.NaN);
        exportXP(path, xpTransFluo, false);
        
    }
    private static void exportXP(String dir, Experiment xp, boolean zip) {
        if (!zip) FileIO.writeToFile(dir+File.separator+xp.getName()+"Config.txt", new ArrayList<Experiment>(){{add(xp);}}, o->JSONUtils.serialize(o));
        else {
            FileIO.ZipWriter w = new FileIO.ZipWriter(dir+File.separator+xp.getName()+".zip");
            w.write("config.json", new ArrayList<Experiment>(1){{add(xp);}}, o->JSONUtils.serialize(o));
            w.close();
        }
    }
}
