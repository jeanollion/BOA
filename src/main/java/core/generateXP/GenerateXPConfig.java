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
package core.generateXP;

import static core.generateXP.GenerateXP.generateXPFluo;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.PreProcessingChain;
import java.io.File;
import java.util.ArrayList;
import utils.FileIO;
import utils.ImportExportJSON;
import utils.JSONUtils;

/**
 *
 * @author jollion
 */
public class GenerateXPConfig {
    public static void main(String[] args) {
        //String path = "/home/jollion/Documents/Fiji.app/plugins/BOA";
        //String path = "/home/jollion/Documents/FijiMapDB3/Fiji.app/plugins/BOA";
        String path = "/data/Images/Fiji.app/plugins/BOA"; // LJP
        Experiment xpFluo = generateXPFluo("MotherMachineMutation", null, true, true, 0, 0, Double.NaN, null);
        exportXP(path, xpFluo, false);
        
        Experiment xpTrans = GenerateXP.generateXPTrans("MotherMachinePhaseContrast", null, true, true, 0, 0, Double.NaN);
        exportXP(path, xpTrans, false);
        
        Experiment xpTransFluo = GenerateXP.generateXPFluo("MotherMachinePhaseContrastAndMutations", null, true, true, 0, 0, Double.NaN, null);
        GenerateXP.setParametersTrans(xpTransFluo, true, false);
        PreProcessingChain ps = xpTransFluo.getPreProcessingTemplate();
        ps.removeAllTransformations();
        GenerateXP.setPreprocessingTransAndMut(ps, true, 0, 0, Double.NaN);
        exportXP(path, xpTransFluo, false);
        
    }
    private static void exportXP(String dir, Experiment xp, boolean zip) {
        if (!zip) FileIO.writeToFile(dir+File.separator+xp.getName()+"Config.txt", new ArrayList<Experiment>(){{add(xp);}}, o->JSONUtils.serialize(o));
        else {
            FileIO.ZipWriter w = new FileIO.ZipWriter(dir+File.separator+xp.getName()+".zip");
            w.write("config.txt", new ArrayList<Experiment>(1){{add(xp);}}, o->JSONUtils.serialize(o));
            w.close();
        }
    }
}
