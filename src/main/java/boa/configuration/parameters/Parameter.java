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
package boa.configuration.parameters;

import boa.configuration.parameters.ui.ParameterUI;
import boa.plugins.ToolTip;
import boa.utils.JSONSerializable;
import java.util.ArrayList;
import java.util.function.Predicate;
import javax.swing.tree.MutableTreeNode;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
/**
 *
 * @author Jean Ollion
 */

public interface Parameter<P extends Parameter<P>> extends MutableTreeNode, JSONSerializable, ToolTip {
    public static final Logger logger = LoggerFactory.getLogger(Parameter.class);
    public ArrayList<Parameter> getPath();
    public ParameterUI getUI();
    public boolean sameContent(Parameter other);
    public void setContentFrom(Parameter other);
    public P duplicate();
    public String getName();
    public void setName(String name);
    public String toStringFull(); 
    public <T extends P> T setToolTipText(String text); //<T extends P> T
    public boolean isValid();
    public boolean isEmphasized();
    public P setEmphasized(boolean isEmphasized);
    public P addValidationFunction(Predicate<P> isValid);
}
