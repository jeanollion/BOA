/*
 * Copyright (C) 2017 jollion
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
package core;

import boa.gui.UserInterface;

/**
 *
 * @author jollion
 */
public interface ProgressCallback {
    public void incrementTaskNumber(int subtask);
    public void incrementProgress();
    public void log(String message);
    public static ProgressCallback get(UserInterface ui) {
        ProgressCallback pcb = new ProgressCallback(){
            int progress = 0;
            int taskCount = 0;
            @Override
            public void incrementTaskNumber(int subtask) {
                taskCount+=subtask;
            }
            @Override
            public void incrementProgress() {
                progress++;
                if (taskCount>0) ui.setProgress((int)(100 * ((double)progress/(double)taskCount)));
            }
            @Override
            public void log(String message) {
                ui.setMessage(message);
            }
        };
        return pcb;
    }
}
