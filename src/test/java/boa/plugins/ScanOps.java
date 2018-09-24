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
package boa.plugins;

import boa.core.Core;
import boa.plugins.ops.OpHelpers;
import static boa.test_utils.TestUtils.logger;
import boa.utils.Utils;
import java.util.Collection;
import java.util.function.Function;
import net.imagej.ops.OpInfo;
import org.scijava.module.ModuleItem;

/**
 *
 * @author Jean Ollion
 */
public class ScanOps {
    public static void main(String[] args) {
        Core.getCore();
        Collection<OpInfo> allOpsInfos = Core.getOpService().infos();
        Function<ModuleItem<?>, Object> toString = c->"Name:"+c.getPersistKey()+"/Type:"+c.getGenericType();;
        for (OpInfo o : allOpsInfos) {
            logger.debug("Op: {}, input: {}, output: {}", o.getName(), Utils.toStringList(OpHelpers.inputs(o), toString), Utils.toStringList(OpHelpers.outputs(o), toString));
        }
    }
}
