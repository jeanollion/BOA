/*
 * Copyright (C) 2016 jollion
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

import dataStructure.configuration.Structure;
import de.caluga.morphium.annotations.Transient;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class SiblingStructureParameter extends StructureParameter {
    int parentStructureIdx=-2;
    @Transient int[] idxStructureMap;
    int selectedStructureIdx=-1;
    boolean includeParent=false;
    
    public SiblingStructureParameter(String name, int selectedStructure, int parentStructureIdx, boolean includeParent, boolean allowNoSelection) {
        super(name, -1, allowNoSelection, false);
        this.parentStructureIdx=parentStructureIdx;
        this.selectedStructureIdx=selectedStructure;
        this.includeParent=includeParent;
    }
    public SiblingStructureParameter(String name, int selectedStructure, boolean includeParent, boolean allowNoSelection) {
        this(name, selectedStructure, -2, includeParent, allowNoSelection);
    }
    public SiblingStructureParameter(String name, boolean includeParent) {
        this(name, -1, -2, includeParent, false);
    }
    public SiblingStructureParameter(String name) {
        this(name, -1, -2, false, false);
    }
    
    @Override public int getSelectedStructureIdx() {
        return selectedStructureIdx;
    }
    
    @Override public void setSelectedIndex(int selectedIndex) {
        super.setSelectedIndex(selectedIndex);
        selectedStructureIdx = getIndexStructureMap()[super.getSelectedIndex()];
    }
    
    @Override public int getSelectedIndex() {
        if (selectedStructureIdx<0) return super.getSelectedIndex();
        else {
            int idx;
            if (selectedIndicies==null) idx= -1;
            else idx= selectedIndicies[0];
            if (idx==-1) {
                getIndexStructureMap();
                if (idxStructureMap!=null) {
                    for (int i = 0; i<idxStructureMap.length; ++i) {
                        if (idxStructureMap[i]==selectedStructureIdx) {
                            super.setSelectedIndex(i);
                            return i;
                        }
                    }
                }
                return -1;
            } else return idx;
        }
    }
    
    public void setParentStructureIdx(int parentStructureIdx) {
        String[] sel = this.getSelectedItemsNames();
        this.parentStructureIdx=parentStructureIdx;
        setIndexStructureMap();
        if (sel.length==1) this.setSelectedItem(sel[0]);
    }
    
    @Override public void setSelectedStructureIdx(int structureIdx) {
        selectedStructureIdx = structureIdx;
    }
    
    protected void setIndexStructureMap() {
        logger.debug("sibling structure parameter: setIndexStructureMap getXP null: {}", getXP()==null);
        if (parentStructureIdx==-2) { //not configured -> look for a Structure in parents
            Structure s= ParameterUtils.getFirstParameterFromParents(Structure.class, this, false);
            if (s!=null) parentStructureIdx = s.getParentStructure();
            else parentStructureIdx = -1;
            //logger.debug("configuring parentStructureIdx: {}", parentStructureIdx);
        }
        if (getXP()==null && idxStructureMap==null) idxStructureMap=new int[]{-1};
        else {
            idxStructureMap =  getXP().getAllChildStructures(parentStructureIdx);
            if (includeParent && parentStructureIdx!=-1) { // add Parent before
                int[] idxStructureMap2 = new int[idxStructureMap.length+1];
                System.arraycopy(idxStructureMap, 0, idxStructureMap2, 1, idxStructureMap.length);
                idxStructureMap2[0] = parentStructureIdx;
                idxStructureMap=idxStructureMap2;
            }
        }
    }
    @Override public void setContentFrom(Parameter other) {
        super.setContentFrom(other);
        if (other instanceof SiblingStructureParameter) {
            SiblingStructureParameter otherP = (SiblingStructureParameter) other;
            parentStructureIdx=otherP.parentStructureIdx;
            selectedStructureIdx = otherP.selectedStructureIdx;
            includeParent=otherP.includeParent;
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    @Override public SiblingStructureParameter setAutoConfiguration(boolean autoConfiguration) {
        this.autoConfiguration=autoConfiguration;
        return this;
    }
    
    @Override protected void autoConfiguration() {
        if (getXP()!=null) {
            Structure s = ParameterUtils.getFirstParameterFromParents(Structure.class, this, false);
            if (s!=null) {
                if (includeParent) this.setSelectedStructureIdx(s.getParentStructure());
                else this.setSelectedStructureIdx(s.getIndex());
            }
        }
    }
    
    protected int[] getIndexStructureMap() {
        if (idxStructureMap==null) setIndexStructureMap();
        return idxStructureMap;
    }
    
    @Override
    public String[] getChoiceList() {
        if (getXP()!=null) {
            return getXP().getStructureNames(getIndexStructureMap());
        } else {
            return new String[]{"error: no xp found in tree"};
        }
    }
    
}
