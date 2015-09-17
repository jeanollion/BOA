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

import boa.gui.configuration.ConfigurationTreeModel;
import configuration.parameters.ui.DocumentFilterIllegalCharacters;
import configuration.parameters.ui.NameEditorUI;
import configuration.parameters.ui.ParameterUI;
import de.caluga.morphium.annotations.Transient;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;

/**
 *
 * @author jollion
 */
public class TextParameter extends SimpleParameter {
    @Transient TextEditorUI ui;
    boolean allowSpecialCharacters;
    String value;
    
    public TextParameter(String name, String defaultText, boolean allowSpecialCharacters) {
        super(name);
        this.value=defaultText;
        this.allowSpecialCharacters=allowSpecialCharacters;
    }
    public ParameterUI getUI() {
        if (ui==null) ui=new TextEditorUI(this, allowSpecialCharacters);
        return ui;
    }

    public boolean sameContent(Parameter other) {
        if (other instanceof TextParameter) return this.value.equals(((TextParameter)other).getValue());
        else return false;
    }

    public void setContentFrom(Parameter other) {
        if (other instanceof TextParameter) this.value=((TextParameter)other).getValue();
        else throw new IllegalArgumentException("wrong parameter type");
    }
    
    public void setValue(String value) {this.value=value;}
    public String getValue() {return value;}
    
    @Override public String toString() {return name+": "+value;}
    
    class TextEditorUI implements ParameterUI { //modified from NameEditorUI

        TextParameter p;
        Object[] component;
        JTextField text;
        ConfigurationTreeModel model;

        public TextEditorUI(TextParameter p_, boolean allowSpecialCharacters) {
            this.p = p_;
            this.model = ParameterUtils.getModel(p);
            text = new JTextField();
            text.setPreferredSize(new Dimension(100, 20));
            if (!allowSpecialCharacters) {
                ((AbstractDocument) text.getDocument()).setDocumentFilter(new DocumentFilterIllegalCharacters());
            }
            this.component = new Component[]{text};
            text.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) {
                    updateText();
                }

                public void removeUpdate(DocumentEvent e) {
                    updateText();
                }

                public void changedUpdate(DocumentEvent e) {
                    updateText();
                }

                private void updateText() {
                    if (text.getText() == null || text.getText().length() == 0) {
                        return;
                    }
                    p.setValue(text.getText());
                    model.nodeChanged(p);
                }
            });
        }

        public Object[] getDisplayComponent() {
            text.setText(p.getName());
            // resize text area..
            text.setPreferredSize(new Dimension(p.getName().length() * 8 + 100, 20));
            return component;
        }

    }
}
