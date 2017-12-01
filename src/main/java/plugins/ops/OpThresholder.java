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
package plugins.ops;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.ConditionalParameter;
import configuration.parameters.Parameter;
import dataStructure.objects.StructureObjectProcessing;
import image.Image;
import java.util.Collection;
import java.util.List;
import net.imagej.ops.OpInfo;
import net.imagej.ops.OpService;
import plugins.Thresholder;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class OpThresholder implements Thresholder, OpWrapper {
    ChoiceParameter method;
    ConditionalParameter cond;
    OpService opService;
    Collection<OpInfo> allOps;
    public OpThresholder() { }
    @Override 
    public void setOpService(OpService os) {
        this.opService=os;
        init();
    }
    public void init() {
        allOps = opService.infos();
        allOps.removeIf(o->!o.isNamespace("threshold"));
        List<String> opNames = Utils.transform(allOps, o->o.getName());
        method = new ChoiceParameter("Threshold method", opNames.toArray(new String[allOps.size()]), null, false);
        cond = new ConditionalParameter(method);
        // TODO: set listener -> when choose an op -> create the parameters, remove the others
    }
    @Override
    public double runThresholder(Image input, StructureObjectProcessing structureObject) {
        
    }

    @Override
    public Parameter[] getParameters() {
        if (cond==null) throw new IllegalArgumentException("Op Service no set, parameters not initiated");
        return new Parameter[]{cond};
    }
    
}
