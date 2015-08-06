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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jollion
 */
public class ParentStructureParameter extends StructureParameter {
    int maxStructure;
    
    public ParentStructureParameter(String name) {
        this(name, -1, 0);
    }
    
    public ParentStructureParameter(String name, int selectedStructure, int maxStructure) {
        super(name, selectedStructure, true, false);
        this.maxStructure=maxStructure;
    }

    public void setMaxStructureIdx(int maxStructure) {
        this.maxStructure = maxStructure;
        if (this.getSelectedIndex()>maxStructure) {
            this.setSelectedIndex(-1);
            Logger.getLogger(getClass().getName()).log(Level.WARNING, "parentStructureParameter:{0}set max structure ({1}) <current selected structure ({2}) -> selected structure set to -1", new Object[]{toString(), maxStructure, this.getSelectedIndex()});
        }
    }
    
    public int getMaxStructureIdx() {
        return maxStructure;
    }
    
    @Override
    public String[] getChoiceList() {
        String[] choices;
        if (getXP()!=null) {
            choices=getXP().getStructuresAsString();
        } else {
           return new String[]{"error: no xp found in tree"};
        }
        if (maxStructure<=0) return new String[]{};
        String[] res = new String[maxStructure];
        System.arraycopy(choices, 0, res, 0, maxStructure);
        return res;
    }
    
    @Override
    public void setSelectedIndex(int structureIdx) {
        if (maxStructure>=0 && structureIdx>maxStructure) throw new IllegalArgumentException("Parent Structure ("+structureIdx+") cannot be superior to max structure ("+maxStructure+")");
        super.setSelectedIndex(structureIdx);
    }
}
