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
 * ChoiceParameter with two elements, 1st = false, 2nd = true
 * @author jollion
 */
public class BooleanParameter extends ChoiceParameter {
    
    public BooleanParameter(String name, boolean defaultValue) {
        super(name, new String[]{"false", "true"}, defaultValue?"true":"false");
    }
    
    public BooleanParameter(String name, String[] listChoice, boolean defaultValue) {
        super(name, listChoice, defaultValue?"true":"false");
        if (listChoice.length!=2) throw new IllegalArgumentException("List choice should be of length 2");
    }
    
    public boolean getSelected() {
        return this.getSelectedIndex()==1;
    }
    
    public void setSelected(boolean selected){
        if (selected) super.setSelectedIndex(1);
        else super.setSelectedIndex(0);
    }
    
    @Override
    public void setCondValue() {
        if (cond!=null) cond.setActionValue(getSelected());
    }
    
    @Override
    public Boolean getValue() {
        return getSelected();
    }
    
    @Override 
    public void setValue(Object value){
        if (value instanceof Boolean) setSelected((Boolean)value);
        if (value instanceof String) super.setValue(value);
    }
    
    // morphia 
    BooleanParameter(){super();}
}
