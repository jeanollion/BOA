/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.core.generateXP;

import static boa.core.generateXP.GenerateXP.generateXPFluo;
import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.PreProcessingChain;
import boa.configuration.parameters.TransformationPluginParameter;
import boa.plugins.PluginFactory;
import boa.plugins.plugins.transformations.CropMicrochannelsPhase2D;
import boa.plugins.plugins.transformations.SimpleCrop;
import boa.plugins.plugins.transformations.SimpleRotationXY;
import java.io.File;
import java.util.ArrayList;
import boa.utils.FileIO;
import boa.utils.ImportExportJSON;
import boa.utils.JSONUtils;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class GenerateXPConfig {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        //String path = "/home/jollion/Fiji.app/plugins/BOA"; // portable
        String path = "/data/Images/Fiji.app/plugins/BOA"; // LJP
        Experiment xpFluo = generateXPFluo("MotherMachineMutation", null, true, 0, 0, Double.NaN, null);
        exportXP(path, xpFluo, false);
        
        Experiment xpTrans = GenerateXP.generateXPPhase("MotherMachinePhaseContrast", null, true, 0, 0, Double.NaN);
        exportXP(path, xpTrans, false);
        
        /*Experiment xpTransFluo = GenerateXP.generateXPFluo("MotherMachinePhaseContrastAndMutations", null, true, 0, 0, Double.NaN, null);
        GenerateXP.setParametersPhase(xpTransFluo, true, false);
        PreProcessingChain ps = xpTransFluo.getPreProcessingTemplate();
        ps.removeAllTransformations();
        GenerateXP.setPreprocessingTransAndMut(ps, 0, 0, Double.NaN);
        exportXP(path, xpTransFluo, false);*/
        
        // XP THOMAS
        /*
        preProcessing -> crop numbers + rotation 90
        microchannels -> der ~50. open/close <2 ? 
        bacteria -> correct head + feature filter on thickness + binary open & close
        Experiment xpJulou = GenerateXP.generateXPTrans("MotherMachinePhaseContrastJULOU", null, true, 0, 0, Double.NaN);
        xpJulou.getChannelImages().insert(xpJulou.getChannelImages().createChildInstance("Fluo"));
        xpJulou.getPreProcessingTemplate().addTransformation(0, 0, null, new SimpleRotationXY(90));
        xpJulou.getPreProcessingTemplate().addTransformation(1, 0, null, new SimpleCrop().yMin(340));
        TransformationPluginParameter<CropMicrochannelsPhase2D> crop = Utils.getFirst(xpJulou.getPreProcessingTemplate().getTransformations(false), t->t.instanciatePlugin() instanceof CropMicrochannelsPhase2D);
        crop.setLocalDerivateXThld(50);
        
        exportXP(path, xpJulou, false);*/
        /*
        MICROSCOPE GE:
        ajouter channel + bacteria proc
        crop -> peak pro = 0.5
        ajouter transfo remove bck + saturate
        ajouter bandpass en premier trackPreFilter bacteria, avec  remove stripes vertical 0.1% + max filt = 150 ? 
        
        */
        
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
