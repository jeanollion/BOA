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

import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.Structure;
import java.util.function.IntConsumer;
import java.util.function.ObjIntConsumer;

/**
 *
 * @author nasique
 */
public class StructureParameter<T extends StructureParameter> extends IndexChoiceParameter {
    protected Experiment xp;
    ObjIntConsumer<T> autoConfiguration;
    
    public StructureParameter(String name) {
        super(name);
    }
    public StructureParameter(String name, int selectedStructure, boolean allowNoSelection, boolean multipleSelection) {
        super(name, selectedStructure, allowNoSelection, multipleSelection);
    }
    
    public StructureParameter(String name, int[] selectedStructures, boolean allowNoSelection) {
        super(name, selectedStructures, allowNoSelection);
    }
    
    public T setAutoConfiguration(ObjIntConsumer<T> autoConfiguration) {
        this.autoConfiguration=autoConfiguration;
        return (T)this;
    }
    public static ObjIntConsumer<StructureParameter> defaultAutoConfiguration() {
        return (p, s)->p.setSelectedStructureIdx(s);
    }
    protected void autoConfiguration() {
        if (getXP()!=null && this.autoConfiguration!=null) {
            Structure s = ParameterUtils.getFirstParameterFromParents(Structure.class, this, false);
            if (s!=null) autoConfiguration.accept((T)this, s.getIndex());
        }
    }
    
    protected Experiment getXP() {
        if (xp==null) xp= ParameterUtils.getExperiment(this);
        return xp;
    }
    
    public void setSelectedStructureIdx(int structureIdx) {
        super.setSelectedIndex(structureIdx);
    }
    
    public int getSelectedStructureIdx() {
        int idx = super.getSelectedIndex();
        if (idx==-1 && !allowNoSelection && getChoiceList().length==1 && getXP()!=null) {
           this.setSelectedStructureIdx(0);
           return 0;
        }
        if (idx==-1 && autoConfiguration!=null) {
            autoConfiguration();
            return super.getSelectedIndex();
        } else return idx;
    }
    
    @Override
    public String[] getChoiceList() {
        String[] choices;
        if (getXP()!=null) {
            choices=getXP().getStructuresAsString();
        } else {
            choices = new String[]{"error, no experiment in the tree"};
        }
        return choices;
    }
    
    public int getParentStructureIdx() {
        if (getXP()==null) logger.error("StructureParameter#getParentStructureIdx(): {}, could not get experiment", name);
        if (getSelectedIndex()==-1) return -1;
        else return getXP().getStructure(getSelectedIndex()).getParentStructure();
    }
    
    public int getFirstCommonParentStructureIdx(int otherStructureIdx) {
        if (getSelectedIndex()==-1 || otherStructureIdx==-1) return -1;
        if (getXP()==null) {
            logger.error("StructureParameter#getParentStructureIdx(): {}, could not get experiment", name);
            return -1;
        }
        else return getXP().getFirstCommonParentStructureIdx(getSelectedIndex(), otherStructureIdx);
    }

    @Override
    public String getNoSelectionString() {
        return "Root";
    }
    
}
