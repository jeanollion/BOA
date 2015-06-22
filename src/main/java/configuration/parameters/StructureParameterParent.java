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

/**
 *
 * @author jollion
 */
public class StructureParameterParent extends StructureParameter {
    int maxStructure;
    public StructureParameterParent(String name, int selectedStructure, int maxStructure) {
        super(name, selectedStructure, true);
        this.maxStructure=maxStructure;
    }

    public void setMaxStructure(int maxStructure) {
        this.maxStructure = maxStructure;
    }
    
    @Override
    public String[] getStructureNames() {
        String[] choices;
        if (getXP()!=null) {
            choices=getXP().getStructuresAsString();
        } else {
            choices = new String[]{"error"}; //no experiment in the tree, make a static method to get experiment...
        }
        if (allowNoSelection) {
            String[] res = new String[maxStructure+1];
            res[0] = "no selection";
            System.arraycopy(choices, 0, res, 1, maxStructure);
            return res;
        } else {
            String[] res = new String[maxStructure];
            System.arraycopy(choices, 0, res, 0, maxStructure);
            return res;
        }
    }

    // morphia
    private StructureParameterParent(){super();}
}
