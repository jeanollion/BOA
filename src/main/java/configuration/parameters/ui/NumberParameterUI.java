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
package configuration.parameters.ui;

import configuration.parameters.BoundedNumberParameter;
import configuration.parameters.NumberParameter;
import configuration.parameters.ParameterUtils;
import configuration.userInterface.ConfigurationTreeModel;
import java.awt.Dimension;
import java.math.BigDecimal;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 *
 * @author nasique
 */
public class NumberParameterUI implements ParameterUI {

    static final int componentMinWith = 80;
    JNumericField number;
    NumberParameter parameter;
    ConfigurationTreeModel model;
    Number lowerBound, upperBound;
    JSlider slider;
    double sliderCoeff;
    boolean editing=false;
    public NumberParameterUI(NumberParameter parameter_) {
        this.parameter = parameter_;
        this.number = new JNumericField(parameter.getDecimalPlaceNumber());
        this.number.setNumber(parameter.getValue());
        this.model = ParameterUtils.getModel(parameter);
        if (parameter instanceof BoundedNumberParameter) {
            lowerBound = ((BoundedNumberParameter)parameter).getLowerBound();
            upperBound = ((BoundedNumberParameter)parameter).getUpperBound();
            if (lowerBound!=null && upperBound!=null) {
                sliderCoeff = Math.pow(10, parameter.getDecimalPlaceNumber());
                slider = new JSlider((int)(lowerBound.doubleValue()*sliderCoeff), getSliderValue(upperBound), getSliderValue(upperBound));
                if (parameter.getValue()!=null) slider.setValue(getSliderValue(parameter.getValue()));
                slider.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        if (editing) return;
                        double d = (slider.getValue()+0.0)/sliderCoeff;
                        number.setNumber(d);
                        parameter.setValue(d);
                    }
                });
            }
        }
        this.number.getDocument().addDocumentListener(new DocumentListener() {

            public void insertUpdate(DocumentEvent e) {
                //System.out.println("insert");
                updateNumber();
            }

            public void removeUpdate(DocumentEvent e) {
                //System.out.println("remove");
                updateNumber();
            }

            public void changedUpdate(DocumentEvent e) {
                //System.out.println("change");
                updateNumber();
            }
        });
    }

    @Override
    public Object[] getDisplayComponent() {
        if (slider==null) return new Object[]{number};
        else return new Object[]{number, slider};
    }

    private void updateNumber() {
        editing = true;
        Number n = number.getNumber();
        if (n != null) {
            if (lowerBound!=null && compare(n, lowerBound)<0) {
                n=lowerBound;
                //number.setNumber(n);
            }
            if (upperBound!=null && compare(n, upperBound)>0) {
                n=upperBound;
                //number.setNumber(n);
            }
            if (slider!=null) slider.setValue(getSliderValue(n));
            parameter.setValue(n);
        }
        number.setPreferredSize(new Dimension(Math.max(componentMinWith, number.getText().length() * 9), number.getPreferredSize().height));
        if (model != null) {
            model.nodeChanged(parameter);
        }
        editing = false;
    }
    private int getSliderValue(Number a) {
        if (a instanceof Integer || a instanceof Short || a instanceof Byte) return (int)(a.intValue()*sliderCoeff);
        else return (int)(a.doubleValue()*sliderCoeff+0.5);
    }
    private int compare(Number a, Number b){
        return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString()));
    }

}
