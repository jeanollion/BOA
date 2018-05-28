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
package boa.configuration.parameters;

import boa.configuration.parameters.ui.ParameterUI;
import boa.plugins.ToolTip;
import boa.utils.JSONSerializable;
import java.util.ArrayList;
import javax.swing.tree.MutableTreeNode;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
/**
 *
 * @author jollion
 */

public interface Parameter extends MutableTreeNode, JSONSerializable, ToolTip {
    public static final Logger logger = LoggerFactory.getLogger(Parameter.class);
    public ArrayList<Parameter> getPath();
    public ParameterUI getUI();
    public boolean sameContent(Parameter other);
    public void setContentFrom(Parameter other);
    public <T extends Parameter> T duplicate();
    public String getName();
    public void setName(String name);
    public String toStringFull();
    public <T extends Parameter> T setToolTipText(String text);
    public boolean isValid();
    public boolean isEmphasized();
    public <T extends Parameter> T setEmphasized(boolean isEmphasized);
}
