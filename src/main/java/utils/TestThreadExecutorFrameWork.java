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
package utils;

import boa.gui.GUI;
import boa.gui.GUIInterface;
import core.DefaultWorker;
import core.DefaultWorker.WorkerTask;
import image.Image;
import image.ImageFloat;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.swing.SwingWorker;
import processing.Filters;

/**
 *
 * @author jollion
 */
public class TestThreadExecutorFrameWork {
    static ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    public static void test() {
        
        
        Worker w = new Worker("A", 10);
        w.execute();
        Worker w2 = new Worker("B", 10);
        w2.execute();
    }
    public static Callable<String> getTask(int taskNumber) {
        return () -> {
            long t0 = System.currentTimeMillis();
            Image im = new ImageFloat("", 200, 200, 10);
            Filters.open(im, im, Filters.getNeighborhood(5, 2, im));
            long t1 = System.currentTimeMillis();
            return taskNumber+":"+(t1-t0)+"ms";
        };
    }
    static class Worker extends SwingWorker<Integer, String> {
        final GUIInterface gui = GUI.getInstance();
        CompletionService<String> completion;
        final int maxTaskIdx;
        final String name;
        public Worker(String name, int maxTaskIdx) {
            this.maxTaskIdx=maxTaskIdx;
            this.name = name;
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
            completion = new ExecutorCompletionService<>(executor);
            
            int count = 0;
            for (int i = 0; i<maxTaskIdx; ++i) {
                publish(name+" submiting task: "+i);
                completion.submit(getTask(i));
            }
            for (int i = 0; i<maxTaskIdx; ++i) {
                publish(name+" retrieving task: "+(i));
                int p = 100 * (++count) / (maxTaskIdx*2);
                publish(name+completion.take().get()+"/"+p);
                //setProgress(p);
            }
            publish(name+" end of first round");
            for (int i = maxTaskIdx; i<maxTaskIdx*2; ++i) {
                publish(name+" submiting task: "+(i));
                completion.submit(getTask(i));
            }
            for (int i = maxTaskIdx; i<maxTaskIdx*2; ++i) {
                publish(name + " retrieving task: "+(i));
                int p = 100 * (++count) / (maxTaskIdx*2);
                publish(name+completion.take().get()+"/p:"+p);
                
                //setProgress(p);
            }
            publish(name+" end of second round");
            return 100;
        }
        
        @Override
        protected void process(List<String> strings) {
            if (gui!=null) {
                for (String s : strings) gui.setMessage(s);
            } 
        }

        @Override 
        public void done() {
            gui.setMessage(name + " all processes done!");
        }
    }
}
