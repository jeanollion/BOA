/*
 * Copyright (C) 2015 nasique
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
import dataStructure.configuration.Structure;
import de.caluga.morphium.annotations.Transient;

/**
 *
 * @author nasique
 */
public class StructureParameter extends IndexChoiceParameter {
    @Transient protected Experiment xp;
    boolean autoConfiguration;
    
    public StructureParameter(String name) {
        super(name);
    }
    public StructureParameter(String name, int selectedStructure, boolean allowNoSelection, boolean multipleSelection) {
        super(name, selectedStructure, allowNoSelection, multipleSelection);
    }
    
    public StructureParameter(String name, int[] selectedStructures, boolean allowNoSelection) {
        super(name, selectedStructures, allowNoSelection);
    }
    
    public StructureParameter setAutoConfiguration(boolean autoConfiguration) {
        this.autoConfiguration=autoConfiguration;
        return this;
    }
    
    protected void autoConfiguration() {
        if (getXP()!=null) {
            Structure s = ParameterUtils.getFirstParameterFromParents(Structure.class, this, false);
            if (s!=null) this.setSelectedStructureIdx(s.getIndex());
        }
    }
    
    protected Experiment getXP() {
        if (xp==null) xp= ParameterUtils.getExperiment(this);
        return xp;
    }
    
    public void setSelectedStructureIdx(int structureIdx) {
        super.setSelectedIndex(structureIdx);
    }
    
    @Override public int getSelectedIndex() {
        int idx = super.getSelectedIndex();
        if (idx==-1 && getChoiceList().length==1 && getXP()!=null) {
           this.setSelectedStructureIdx(0);
           return 0;
        }
        if (idx==-1 && autoConfiguration) {
            autoConfiguration();
            return super.getSelectedIndex();
        }
        return idx;
    }
    
    public int getSelectedStructureIdx() {
        return this.getSelectedIndex();
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
    
    @Override public void setContentFrom(Parameter other) {
        super.setContentFrom(other);
        if (other instanceof StructureParameter) {
            StructureParameter otherP = (StructureParameter) other;
            autoConfiguration = otherP.autoConfiguration;
        } else throw new IllegalArgumentException("wrong parameter type");
    }
}
