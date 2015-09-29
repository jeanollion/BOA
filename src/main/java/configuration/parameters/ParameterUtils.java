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
import boa.gui.configuration.ConfigurationTreeModel;
import boa.gui.configuration.TreeModelContainer;
import static configuration.parameters.Parameter.logger;
import dataStructure.configuration.MicroscopyField;
import java.util.ArrayList;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class ParameterUtils {

    public static ConfigurationTreeModel getModel(Parameter p) {
        if (p instanceof TreeModelContainer) {
            return ((TreeModelContainer) p).getModel();
        }
        Parameter root = p;
        while (root.getParent() != null) {
            root = (Parameter) root.getParent();
            if (root instanceof TreeModelContainer) {
                return ((TreeModelContainer) root).getModel();
            }
        }
        return null;
    }

    public static Experiment getExperiment(Parameter p) {
        if (p instanceof Experiment) {
            return (Experiment) p;
        }
        Parameter root = p;
        //logger.trace("getExperiment: {}", p.getName());
        while (root.getParent() != null) {
            root = (Parameter) root.getParent();
            //logger.trace("getExperiment: {}", root.getName());
            if (root instanceof Experiment) {
                return (Experiment) root;
            }
        }
        return null;
    }
    
    public static MicroscopyField getMicroscopyFiedl(Parameter p) {
        if (p instanceof MicroscopyField) {
            return (MicroscopyField) p;
        }
        Parameter parent = p;
        while (parent.getParent() != null) {
            parent = (Parameter) parent.getParent();
            if (parent instanceof MicroscopyField) {
                return (MicroscopyField) parent;
            }
        }
        return null;
    }
    
    public static int getTimePointNumber(Parameter p) {
        MicroscopyField f = getMicroscopyFiedl(p);
        if (f!=null) {
            return f.getTimePointNumber();
        } else {
            Experiment xp = getExperiment(p);
            if (xp==null) {
                logger.warn("parameter: {}, no experient found in tree to get timePoint number", p.getName());
                return 0;
            }
            else return xp.getTimePointNumber();
        }
    }

    public static void setContent(Parameter[] recieve, Parameter[] give) {
        for (int i = 0; i < recieve.length; i++) {
            recieve[i].setContentFrom(give[i]);
        }
    }
    
    public static void setContent(ArrayList<Parameter> recieve, ArrayList<Parameter> give) {
        for (int i = 0; i < recieve.size(); i++) {
            recieve.get(i).setContentFrom(give.get(i));
        }
    }

    public static Parameter[] duplicateArray(Parameter[] parameters) {
        Parameter[] res = new Parameter[parameters.length];
        for (int i = 0; i < parameters.length; ++i) {
            res[i] = parameters[i].duplicate();
        }
        return res;
    }

    public static boolean arraysEqual(int[] array1, int[] array2) {
        if (array1.length == array2.length) {
            for (int i = 0; i < array1.length; ++i) {
                if (array1[i] != array2[i]) {
                    return false;
                }
            }
            return true;
        } else {
            return false;
        }
    }

    public static String[] createChoiceList(int startElement, int endElement) {
        String[] res = new String[endElement - startElement + 1];
        int paddingSize = String.valueOf(endElement).length();
        for (int i = startElement; i <= endElement; ++i) {
            res[i - startElement] = Utils.formatInteger(paddingSize, i);
        }
        return res;
    }

    public static Object duplicateConfigurationData(Object in) {
        if (in != null) {
            if (in instanceof Number) {
                if (in instanceof Double || in instanceof Float) {
                    return ((Number) in).doubleValue();
                } else if (in instanceof Long) {
                    return ((Number) in).longValue();
                } else {
                    return ((Number) in).intValue();
                }
            } else if (in instanceof String) {
                return (String) in;
            } else if (in.getClass().isArray()) {
                if (in instanceof Object[]) {
                    return duplicateConfigurationDataArray((Object[]) in);
                }
            } else if (in instanceof int[]) {
                int length = ((int[]) in).length;
                int[] res = new int[length];
                System.arraycopy(in, 0, res, 0, length);
                return res;
            } else if (in instanceof double[]) {
                int length = ((double[]) in).length;
                double[] res = new double[length];
                System.arraycopy(in, 0, res, 0, length);
                return res;
            }
        }
        return null;
    }

    public static Object[] duplicateConfigurationDataArray(Object[] in) {
        if (in != null) {
            Object[] res = new Object[in.length];
            for (int i = 0; i < res.length; ++i) {
                res[i] = duplicateConfigurationData(in[i]);
            }
            return res;
        } else {
            return null;
        }
    }
}
