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
package boa.plugins.plugins.thresholders;

import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.Structure;
import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.ConditionalParameter;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ParameterUtils;
import boa.configuration.parameters.ParentStructureParameter;
import boa.configuration.parameters.PluginParameter;
import boa.configuration.parameters.StructureParameter;
import boa.data_structure.Selection;
import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectProcessing;
import boa.data_structure.StructureObjectUtils;
import boa.image.Histogram;
import boa.image.HistogramFactory;
import boa.image.Image;
import boa.image.ImageMask;
import boa.plugins.SimpleThresholder;
import boa.plugins.Thresholder;
import boa.plugins.ThresholderHisto;
import boa.utils.JSONUtils;
import boa.utils.Utils;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 *
 * @author Jean Ollion
 */
public class ParentThresholder implements Thresholder {
    public ParentStructureParameter parent = new ParentStructureParameter("Run Thresholder On Parent:").setAutoConfiguration(ParentStructureParameter.defaultAutoConfigurationParent());
    public ParentStructureParameter structureIdx = new ParentStructureParameter("Run Thresholder On Image:").setAutoConfiguration((p)->{int s = StructureParameter.structureInParents().applyAsInt(p); p.setMaxStructureIdx(s+1); p.setSelectedIndex(s);}).setAllowNoSelection(false);
    public BooleanParameter runThresholderOnWholeTrack = new BooleanParameter("Run On:", "Whole Track", "Each Object Separately", true);
    public PluginParameter<Thresholder> thresholder = new PluginParameter("Thresholder", Thresholder.class, false);
    public PluginParameter<ThresholderHisto> thresholderHisto = new PluginParameter("Thresholder", ThresholderHisto.class, false);
    ConditionalParameter cond = new ConditionalParameter(runThresholderOnWholeTrack).setActionParameters("Whole Track", thresholderHisto).setActionParameters("Each Object Separately", thresholder);
    Parameter[] parameters = new Parameter[]{parent, structureIdx, cond};
    
    public ParentThresholder setIndividualThresholder(Thresholder thresholder) {
        this.thresholder.setPlugin(thresholder);
        return this;
    }
    
    public ParentThresholder setTrackThresholder(ThresholderHisto thresholder) {
        this.thresholderHisto.setPlugin(thresholder);
        return this;
    }
    
    public ParentThresholder() {
        /*parent.addListener((p)-> { // parent should be a parent from this structure
            if (parent.getSelectedIndex()>=0) {
                Structure s = ParameterUtils.getFirstParameterFromParents(Structure.class, p, false);
                Experiment xp = ParameterUtils.getExperiment(s);
                if (s!=null) {
                    int sIdx = s.getIndex();
                    if (!xp.isChildOf(parent.getSelectedIndex(), sIdx)) {
                        parent.setSelectedIndex(-1);
                    }
                }
            }
            //checkStructureIdx();
        });*/
        //structureIdx.addListener((p)-> checkStructureIdx());
    }
    
    /*private void checkStructureIdx() {
        if (parent.getSelectedIndex()>=0 && structureIdx.getSelectedIndex()>=0) {
            Experiment xp = ParameterUtils.getExperiment(parent);
            if (!xp.isDirectChildOf(parent.getSelectedIndex(), structureIdx.getSelectedIndex())) {
                structureIdx.setSelectedIndex(-1);
            }
        }
    }*/
    
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        StructureObject p = ((StructureObject)structureObject).getParent(parent.getSelectedIndex());
        int sIdx = structureIdx.getSelectedIndex();
        if (runThresholderOnWholeTrack.getSelected()) {
            p = p.getTrackHead();
            List<StructureObject> track = StructureObjectUtils.getTrack(p, false);
            String key = JSONUtils.toJSON(Arrays.asList(parameters)).toJSONString()+Utils.toStringList(track, s->Selection.indicesString(s)); // key involves configuration + track
            if (!p.getAttributes().containsKey(key)) { // compute threshold on whole track
                synchronized(p.getAttributes()) {
                    // get track histogram
                    Map<Image, ImageMask> map = track.stream().collect(Collectors.toMap(o->o.getRawImage(sIdx), o->o.getMask()));
                    Histogram  histo = HistogramFactory.getHistogram(()->Image.stream(map, true).parallel(), HistogramFactory.BIN_SIZE_METHOD.AUTO_WITH_LIMITS);
                    double thld = this.thresholderHisto.instanciatePlugin().runThresholderHisto(histo);
                    logger.debug("computing thld : {}, thresholder {} on track: {}, key: {}", thld, this.thresholderHisto.getPluginName(), p, key);
                    p.setAttribute(key, thld);
                }
            }
            return p.getAttribute(key, Double.NaN);
        } else { 
            String key = JSONUtils.toJSON(Arrays.asList(parameters)).toJSONString();
            if (!p.getAttributes().containsKey(key)) { // compute threshold on single object
                synchronized(p.getAttributes()) {
                    double thld = thresholder.instanciatePlugin().runThresholder(p.getRawImage(sIdx), p);
                    logger.debug("computing : threshold {}, thresholder: {}, on object: {}, key: {}", thld, this.thresholderHisto.getPluginName(), p , key);
                    
                    p.setAttribute(key, thld);
                }
            }
            return p.getAttribute(key, Double.NaN);
        }
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
