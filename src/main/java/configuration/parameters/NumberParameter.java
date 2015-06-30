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
import configuration.parameters.ui.ParameterUI;
import configuration.userInterface.ConfigurationTreeModel;
import java.awt.Dimension;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.mongodb.morphia.annotations.Transient;

/**
 *
 * @author jollion
 */
public class NumberParameter extends SimpleParameter {
    @Transient FloatParameterUI ui;
    Number value;
    int decimalPlaces;
    
    public NumberParameter(String name, int decimalPlaces) {
        super(name);
        this.decimalPlaces=decimalPlaces;
    }
    
    public NumberParameter(String name, int decimalPlaces, Number defaultValue) {
        this(name, decimalPlaces);
        this.value=defaultValue;
    }
    
    public ParameterUI getUI() {
        if (ui==null) ui=new FloatParameterUI(this);
        return ui;
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
    
    public boolean sameContent(Parameter other) {
        if (other instanceof NumberParameter) return ((NumberParameter)other).getValue()==getValue();
        else return false;
    }

    public void setContentFrom(Parameter other) {
        if (other instanceof NumberParameter) this.value=((NumberParameter)other).getValue();
    }

    public Parameter duplicate() {
        return new NumberParameter(this.name, decimalPlaces, value);
    }
    
    class FloatParameterUI implements ParameterUI {
        JNumericField number;
        NumberParameter parameter;
        ConfigurationTreeModel model;
        public FloatParameterUI(NumberParameter parameter_) {
            this.parameter=parameter_;
            this.number=new JNumericField(parameter.decimalPlaces);
            this.number.setNumber(parameter.getValue());
            this.model= ParameterUtils.getModel(parameter);
            this.number.getDocument().addDocumentListener(new DocumentListener() {

                public void insertUpdate(DocumentEvent e) {
                    updateNumber();
                }

                public void removeUpdate(DocumentEvent e) {
                    updateNumber();
                }

                public void changedUpdate(DocumentEvent e) {
                    updateNumber();
                }
            });
        }
        @Override
        public Object[] getDisplayComponent() {
            return new Object[]{number};
        }
        
        private void updateNumber() {
            parameter.setValue(number.getNumber());
            number.setPreferredSize(new Dimension(number.getText().length()*9, number.getPreferredSize().height));
            if (model!=null) model.nodeChanged(parameter);
        }
    }

}
