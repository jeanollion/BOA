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
import de.caluga.morphium.annotations.Transient;

/**
 *
 * @author nasique
 */
public class TimePointParameter extends IndexChoiceParameter {
    @Transient protected Experiment xp;
    
    public TimePointParameter(String name) {
        super(name);
    }
    public TimePointParameter(String name, int selectedTimePoint, boolean allowNoSelection, boolean multipleSelection) {
        super(name, selectedTimePoint, allowNoSelection, multipleSelection);
    }
    
    public TimePointParameter(String name, int[] selectedTimePoints, boolean allowNoSelection) {
        super(name, selectedTimePoints, allowNoSelection);
    }
    
    protected Experiment getXP() {
        if (xp==null) xp= ParameterUtils.getExperiment(this);
        return xp;
    }
    
    @Override 
    public int getSelectedIndex() {
        if (getXP()!=null && getXP().getTimePointNumber()<=super.getSelectedIndex()) return getXP().getTimePointNumber()-1;
        else return super.getSelectedIndex();
    }
    
    @Override
    public String[] getChoiceList() {
        String[] choices;
        if (getXP()!=null) {
            choices=ParameterUtils.createChoiceList(0, getXP().getTimePointNumber());
        } else {
            choices = new String[]{"error, no experiment in the tree"}; //no experiment in the tree, make a static method to get experiment...
        }
        return choices;
    }
    
}
