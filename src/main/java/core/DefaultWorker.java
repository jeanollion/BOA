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

import boa.gui.GUI;
import boa.gui.GUIInterface;
import ij.IJ;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.List;
import javax.swing.SwingWorker;
import utils.ArrayUtil;

/**
 *
 * @author jollion
 */
public class DefaultWorker extends SwingWorker<Integer, String>{
    final WorkerTask task;
    int[] taskIdx;
    GUIInterface gui;
    public static void execute(WorkerTask t, int maxTaskIdx) {
        new DefaultWorker(t, maxTaskIdx, GUI.hasInstance()?GUI.getInstance():null).execute();
    }
    public DefaultWorker(WorkerTask task, int maxTaskIdx, GUIInterface gui) {
        this.task=task;
        taskIdx = ArrayUtil.generateIntegerArray(0, maxTaskIdx);
        if (gui!=null) {
            addPropertyChangeListener(new PropertyChangeListener() {
                @Override    
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("progress".equals(evt.getPropertyName())) {
                        int progress = (Integer) evt.getNewValue();
                        gui.setProgress(progress);
                    }
                }
            });
        }
    }
    @Override
    protected Integer doInBackground() throws Exception {
        int count = 0;
        for (int i : taskIdx) {
            task.run(i);
            setProgress(100 * (++count) / taskIdx.length);
        }
        return 0;
    }
    
    @Override
    protected void process(List<String> strings) {
        if (gui!=null) {
            for (String s : strings) gui.setMessage(s);
        } 
    }

    public static interface WorkerTask {
        public void run(int i);
    }
}
