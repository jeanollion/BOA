/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.core.generateXP;

import static boa.core.generateXP.GenerateXP.generateXPFluo;
import boa.configuration.experiment.Experiment;
import boa.plugins.PluginFactory;
import java.io.File;
import java.util.ArrayList;
import boa.utils.FileIO;
import boa.utils.ImportExportJSON;
import boa.utils.JSONUtils;
import boa.utils.Utils;

/**
 *
 * @author Jean Ollion
 */
public class GenerateXPConfig {
    public static void main(String[] args) {
        PluginFactory.findPlugins("boa.plugins.plugins");
        String path = "/home/jollion/Fiji.app/plugins/BACMMAN"; // portable
        //String path = "/data/Images/Fiji.app/plugins/BACMMAN"; // LJP
        Experiment xpFluo = generateXPFluo("MotherMachineMutation", null, true, false, 0, 0, Double.NaN, null);
        exportXP(path, xpFluo, false);
        
        Experiment xpFluoHN = generateXPFluo("MotherMachineMutationHighBck", null, true, true, 0, 0, Double.NaN, null);
        exportXP(path, xpFluoHN, false);
        
        Experiment xpPhase = GenerateXP.generateXPPhase("MotherMachinePhaseContrast", null, true, 0, 0, Double.NaN);
        exportXP(path, xpPhase, false);
        
        // dataset config for nature protocol
        String pathDS = "/data/Images/ExampleDatasets/";
        Experiment xpFluoBactOnly = xpFluo.duplicate();
        xpFluoBactOnly.setName("dataset3");
        xpFluoBactOnly.getChannelImages().remove(1);
        xpFluoBactOnly.getStructures().remove(2);
        xpFluoBactOnly.getPreProcessingTemplate().getTransformations().remove(3);
        xpFluoBactOnly.getPreProcessingTemplate().getTransformations().remove(1);
        xpFluoBactOnly.getPreProcessingTemplate().getTransformations().remove(0);
        xpFluoBactOnly.getMeasurements().removeAllElements();
        exportXP(pathDS+"/dataset3/", "config_dataset3.json", xpFluoBactOnly, false);
        exportXP(pathDS+"/dataset1/", "config_dataset1.json", xpPhase, false);
        exportXP(pathDS+"/dataset2/", "config_dataset2.json", xpFluo, false);
        
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
        if (!zip) exportXP(dir, xp.getName()+"Config.txt", xp, false);
        else exportXP(dir+File.separator+ xp.getName()+"Config.zip", "config.json", xp, true);
    }
    private static void exportXP(String dir, String fileName, Experiment xp, boolean zip) {
        if (!zip) FileIO.writeToFile(dir+File.separator+fileName, new ArrayList<Experiment>(){{add(xp);}}, o->JSONUtils.serialize(o));
        else {
            FileIO.ZipWriter w = new FileIO.ZipWriter(dir);
            w.write(fileName, new ArrayList<Experiment>(1){{add(xp);}}, o->JSONUtils.serialize(o));
            w.close();
        }
    }
}
