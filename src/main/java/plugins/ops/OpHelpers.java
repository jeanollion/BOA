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

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.Parameter;
import java.lang.reflect.Type;
import java.util.List;
import net.imagej.ops.OpInfo;
import net.imagej.ops.OpService;
import org.scijava.ItemIO;
import org.scijava.module.ModuleItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thredds.filesystem.FileSystemProto;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class OpHelpers {
    public static final Logger logger = LoggerFactory.getLogger(OpHelpers.class);
    final OpService service;
    public OpHelpers(OpService service) {
        this.service=service;
    }
    public ParameterWithValue[] getParameters(OpInfo info) {
        List<ModuleItem<?>> params = info.inputs();
        params.removeIf(p->!p.isPersisted()||p.getIOType()!=ItemIO.INPUT);
        List<Parameter> res = Utils.transform(params, p->mapParameter(p));
        res.removeIf(o->o==null);
        return res.toArray(new ParameterWithValue[res.size()]);
    }
    public static ParameterWithValue mapParameter(ModuleItem<?> param) {
        if (param.getType()==double.class) { // get generic type ? 
            Double lower = (Double)param.getMinimumValue();
            Double upper = (Double)param.getMaximumValue();
            logger.debug("lower: {} upper: {}, default: {}", param.getMinimumValue(), param.getMaximumValue(), param.getDefaultValue());
            return new BoundedNumberParameter(param.getName(), 5, (Double)param.getDefaultValue(), lower, upper);
        }
        // TODO make ints, boolean, string, choice, Arrays! (fixed or user defined size? list?) make special for outofbounds: type choice parameter that can create an outofbound factory
        return null;
    }
    // TODO: make populate arguments, including non parameters (input). 
    // TODO: make a function for filters (Binary), thresholds (Unary), segmenters? (Binary)
}
