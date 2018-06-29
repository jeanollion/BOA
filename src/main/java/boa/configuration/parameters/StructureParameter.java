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
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.ObjIntConsumer;
import java.util.function.ToIntFunction;

/**
 *
 * @author nasique
 */
public class StructureParameter<T extends StructureParameter<T>> extends IndexChoiceParameter {
    protected Experiment xp;
    Consumer<T> autoConfiguration;
    
    public StructureParameter(String name) {
        super(name);
    }
    public StructureParameter(String name, int selectedStructure, boolean allowNoSelection, boolean multipleSelection) {
        super(name, selectedStructure, allowNoSelection, multipleSelection);
    }
    
    public StructureParameter(String name, int[] selectedStructures, boolean allowNoSelection) {
        super(name, selectedStructures, allowNoSelection);
    }
    
    @Override
    public boolean isValid() {
        if (!super.isValid()) return false;
        // also check selected indices are within index choice range
        if (this.selectedIndices==null) return true;
        String[] ch = this.getChoiceList();
        if (ch!=null) {
            for (int i : selectedIndices) if (i>=ch.length) return false;
        } else return false;
        return true;
    }
    
    public T setAutoConfiguration(Consumer<T> autoConfiguration) {
        this.autoConfiguration=autoConfiguration;
        return (T)this;
    }
    public static <T extends StructureParameter<T>> Consumer<T> defaultAutoConfiguration() {
        return p -> p.setSelectedStructureIdx(structureInParents().applyAsInt(p));
    }
    public static ToIntFunction<StructureParameter> structureInParents() {
        return p->{
            Structure s = ParameterUtils.getFirstParameterFromParents(Structure.class, p, false);
            int sIdx = (s!=null) ? s.getIndex(): -1;
            return sIdx;
        };
    }
    public static ToIntFunction<StructureParameter> structureParameterInParents() {
        return p->{
            StructureParameter s = ParameterUtils.getFirstParameterFromParents(StructureParameter.class, p, false);
            int sIdx = (s!=null) ? s.getSelectedStructureIdx(): -1;
            return sIdx;
        };
    }
    protected void autoConfiguration() {
        if (autoConfiguration!=null) autoConfiguration.accept((T)this);
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
            choices = new String[]{"error, no experiment in the configuration"};
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
