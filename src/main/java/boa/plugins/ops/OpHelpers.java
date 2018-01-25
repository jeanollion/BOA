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
package boa.plugins.ops;

import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.Parameter;
import java.lang.reflect.Type;
import java.util.List;
import net.imagej.ops.OpInfo;
import net.imagej.ops.OpService;
import net.imglib2.RandomAccessible;
import org.scijava.ItemIO;
import org.scijava.module.ModuleItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import thredds.filesystem.FileSystemProto;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class OpHelpers {
    // need further version of IJ2 to work
    /*
    public static final Logger logger = LoggerFactory.getLogger(OpHelpers.class);
    final OpService service;
    public OpHelpers(OpService service) {
        this.service=service;
    }
    public static OpParameter[] getParameters(OpInfo info) {
        
        List<ModuleItem<?>> params = info.inputs();
        params.removeIf(p->!p.isPersisted()||p.getIOType()!=ItemIO.INPUT);
        List<OpParameter> res = Utils.transform(params, p->mapParameter(p));
        res.removeIf(o->o==null);
        return res.toArray(new OpParameter[res.size()]);
    }
    public static OpParameter mapParameter(ModuleItem<?> param) {
        OpParameter res=null;
        if (param.getType()==double.class || param.getType()==Double.class) { // get generic type ? 
            Double lower = (Double)param.getMinimumValue();
            Double upper = (Double)param.getMaximumValue();
            logger.debug("lower: {} upper: {}, default: {}", param.getMinimumValue(), param.getMaximumValue(), param.getDefaultValue());
            res =  new BoundedNumberParameter(param.getName(), 10, (Double)param.getDefaultValue(), lower, upper);
        } if (param.getType()==long.class || param.getType()==Long.class) { // get generic type ? 
            Long lower = (Long)param.getMinimumValue();
            Long upper = (Long)param.getMaximumValue();
            logger.debug("lower: {} upper: {}, default: {}", param.getMinimumValue(), param.getMaximumValue(), param.getDefaultValue());
            res =  new BoundedNumberParameter(param.getName(), 0, (Long)param.getDefaultValue(), lower, upper);
        } if (param.getType()==int.class || param.getType()==Integer.class) { // get generic type ? 
            Integer lower = (Integer)param.getMinimumValue();
            Integer upper = (Integer)param.getMaximumValue();
            logger.debug("lower: {} upper: {}, default: {}", param.getMinimumValue(), param.getMaximumValue(), param.getDefaultValue());
            res =  new BoundedNumberParameter(param.getName(), 0, (Integer)param.getDefaultValue(), lower, upper);
        }
        // TODO make ints, boolean, string, choice, Arrays! (fixed or user defined size? list?) make special for outofbounds: type choice parameter that can create an outofbound factory
        if (res!=null) res.setModuleItem(param);
        logger.debug("param: {} ({}), could be converted ? {}", param.getName(), param.getType().getSimpleName(), res!=null);
        return res;
    }
    // TODO: make populate arguments, including non parameters (input). 
    // TODO: make a function for filters (Binary), thresholds (Unary), segmenters? (Binary)
    
    public static boolean isImage(ModuleItem<?> param) {
        return RandomAccessible.class.isAssignableFrom(param.getType());
    }
    */
}
