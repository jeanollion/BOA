/*
 * Copyright (C) 2018 jollion
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
package boa.gui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author jollion
 */
public class MultiUserInterface  implements UserInterface {
    List<UserInterface> uis = new ArrayList<>();
    public MultiUserInterface(UserInterface... uis) {
        this.uis.addAll(Arrays.asList(uis));
    }
    public MultiUserInterface addUIs(UserInterface... uis) {
        this.uis.addAll(Arrays.asList(uis));
        return this;
    }
    @Override
    public void setProgress(int i) {
        uis.stream().forEach((ui) -> {ui.setProgress(i);});
    }

    @Override
    public void setMessage(String message) {
        uis.stream().forEach((ui) -> {ui.setMessage(message);});
    }

    @Override
    public void setRunning(boolean running) {
        uis.stream().forEach((ui) -> {ui.setRunning(running);});
    }
    
}
