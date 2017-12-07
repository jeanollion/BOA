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
package plugins.plugins.thresholders;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.Parameter;
import core.Core;
import dataStructure.objects.StructureObjectProcessing;
import image.Image;
import image.ImageMask;
import image.ImgLib2ImageWrapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.imagej.ops.OpInfo;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessible;
import net.imglib2.histogram.BinMapper1d;
import net.imglib2.histogram.Histogram1d;
import net.imglib2.histogram.Integer1dBinMapper;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import org.scijava.module.ModuleItem;
import plugins.SimpleThresholder;
import plugins.Thresholder;
import plugins.ops.OpHelpers;
import plugins.ops.OpParameter;
import plugins.ops.OpWrapper;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class OpThresholder implements SimpleThresholder {
    ChoiceParameter method;
    ConditionalParameter cond;
    Map<String, OpInfo> allOps;
    public OpThresholder() {
        init();
    }

    private void init() {
        Collection<OpInfo> allOpsInfos = Core.getOpService().infos();
        allOpsInfos.removeIf(o->!o.isNamespace("threshold")||"apply".equals(o.getSimpleName())||(o.outputs().size()==1 && (o.outputs().get(0).getType()==RealType.class || Number.class.isAssignableFrom(o.outputs().get(0).getType()))));
        
        //net.imagej.ops.threshold.ApplyThresholdMethod;
        //allOps = new TreeMap<>(allOpsInfos.stream().collect(Collectors.toMap(o->o.getName(), o->o)));
        allOps = new TreeMap<>();
        for (OpInfo info : allOpsInfos) {
            if (allOps.containsKey(info.getSimpleName())) {
                logger.debug("duplicate key: {} with: {}", info, allOps.get(info.getSimpleName()));
            } 
            allOps.put(info.getSimpleName(), info);
        }
        method = new ChoiceParameter("Threshold method", allOps.keySet().toArray(new String[allOps.size()]), null, false);
        cond = new ConditionalParameter(method);
        cond.addListener(p->{
            String m = method.getSelectedItem();
            if (cond.getActionParameters(m)==null) cond.setActionParameters(m, OpHelpers.getParameters(allOps.get(m)));
        });
        // TODO: set listener -> when choose an op -> create the parameters, remove the others
    }
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        return runSimpleThresholder(input, structureObject==null?null:structureObject.getMask());
    }

    @Override
    public Parameter[] getParameters() {
        if (cond==null) throw new IllegalArgumentException("Op Service no set, parameters not initiated");
        return new Parameter[]{cond};
    }

    @Override
    public double runSimpleThresholder(Image image, ImageMask mask) {
        String m = method.getSelectedItem();
        OpInfo info = allOps.get(m);
        List<ModuleItem<?>> inputs = info.inputs();
        Map<String, Object> values = cond.getParameters(m).stream().collect(Collectors.toMap(o->((OpParameter)o).getModuleItem().getName(), o->((OpParameter)o).getValue()));
        Object[] args = new Object[inputs.size()];
        for (int i = 0; i<inputs.size(); ++i) {
            ModuleItem<?> param = inputs.get(i);
            if (OpHelpers.isImage(param)) args[i] = ImgLib2ImageWrapper.getImage(image);
            else if (param.getType() == Histogram1d.class) {
                
            }
            else {
                args[i] = values.get(param.getName());
                logger.debug("parameter: {} {}, value: {}");
            }
        }
        Object res = Core.getOpService().run(info.getName(), args);
        if (res instanceof RealType) return ((RealType)res).getRealDouble();
        else if (res instanceof Number) return ((Number)res).doubleValue();
        else return Double.NaN;
    }

}
