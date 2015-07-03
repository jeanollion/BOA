/*
 * Copyright (C) 2015 jollion
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
package configuration.parameters;

import dataStructure.configuration.Experiment;
import configuration.userInterface.ConfigurationTreeModel;
import configuration.userInterface.TreeModelContainer;

/**
 *
 * @author jollion
 */
public class ParameterUtils {
    public static ConfigurationTreeModel getModel(Parameter p) {
        if (p instanceof TreeModelContainer) return ((TreeModelContainer)p).getModel();
        Parameter root=p;
        while(root.getParent()!=null) {
            root = (Parameter)root.getParent();
            if (root instanceof TreeModelContainer) {
                return ((TreeModelContainer)root).getModel();
            }
        }
        return null;
    }
    
    public static Experiment getExperiment(Parameter p) {
        if (p instanceof Experiment) return (Experiment)p;
        Parameter root=p;
        while(root.getParent()!=null) {
            root = (Parameter)root.getParent();
            if (root instanceof Experiment) {
                return (Experiment)root;
            }
        }
        return null;
    }
    
    public static void setContent(Parameter[] recieve, Parameter[] give) {
        for (int i = 0; i<recieve.length;i++) recieve[i].setContentFrom(give[i]);
    }
    public static Parameter[] duplicateArray(Parameter[] parameters) {
        Parameter[] res = new Parameter[parameters.length];
        for (int i = 0; i<parameters.length; ++i) res[i]=parameters[i].duplicate();
        return res;
    }
}
