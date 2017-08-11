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

import configuration.parameters.ui.JNumericField;
import configuration.parameters.ui.NumberParameterUI;
import configuration.parameters.ui.ParameterUI;
import boa.gui.configuration.ConfigurationTreeModel;
import java.awt.Dimension;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import de.caluga.morphium.annotations.Transient;

/**
 *
 * @author jollion
 */
public class NumberParameter extends SimpleParameter {
    //@Transient NumberParameterUI ui;
    Number value;
    @Transient int decimalPlaces;
    
    public NumberParameter(String name, int decimalPlaces) {
        super(name);
        this.decimalPlaces=decimalPlaces;
    }
    
    public NumberParameter(String name, int decimalPlaces, Number defaultValue) {
        this(name, decimalPlaces);
        this.value=defaultValue;
    }
    
    public ParameterUI getUI() {
        return new NumberParameterUI(this);
    }
    
    public int getDecimalPlaceNumber() {
        return decimalPlaces;
    }
    
    public Number getValue() {
        return value;
    }
    
    public void setValue(Number value) {
        this.value=value;
    }
    
    @Override
    public String toString() {
        return name+": "+value;
    }
    
    public boolean hasIntegerValue() {return (getValue().doubleValue()-getValue().intValue())!=0;}
    
    public boolean sameContent(Parameter other) {
        if (other instanceof NumberParameter) return ((NumberParameter)other).getValue()==getValue();
        else return false;
    }

    public void setContentFrom(Parameter other) {
        //if (other!=null) Parameter.logger.trace("Parameter {} set content from {}", this.getClass(), other.getClass());
        if (other instanceof NumberParameter) {
            this.value=((NumberParameter)other).getValue();
            //this.decimalPlaces = ((NumberParameter)other).decimalPlaces;
        }
    }
    
    @Override public NumberParameter duplicate() {
        return new NumberParameter(name, decimalPlaces, value);
    }

    @Override
    public Object toJSONEntry() {
        return value;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        this.value=(Number)jsonEntry;
    }
    
}
