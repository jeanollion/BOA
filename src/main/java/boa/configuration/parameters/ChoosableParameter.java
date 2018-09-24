/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.configuration.parameters;

/**
 *
 * @author jollion
 */
public interface ChoosableParameter<P extends Parameter<P>> extends Parameter<P>, Listenable<P> {
    /**
     * Set {@param item} as selected
     * Should also fire listeners
     * @param item 
     */
    public void setSelectedItem(String item);
    public String[] getChoiceList();
    public int getSelectedIndex();
    public boolean isAllowNoSelection();
    public String getNoSelectionString();
}
