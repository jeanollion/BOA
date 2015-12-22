/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
/**
Copyright (C) Jean Ollion

License:
This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

public class ThreadRunner {

    /** Start all given threads and wait on each of them until all are done.
     * From Stephan Preibisch's Multithreading.java class. See:
     * http://repo.or.cz/w/trakem2.git?a=blob;f=mpi/fruitfly/general/MultiThreading.java;hb=HEAD
     */

    /* To initiate Threads:
    final ThreadRunner tr = new ThreadRunner(0, sizeZ, multithread?0:1);
    for (int i = 0; i<tr.threads.length; i++) {
        tr.threads[i] = new Thread(
            new Runnable() {
                public void run() {
                    for (int idx = tr.ai.getAndIncrement(); idx<tr.end; idx = tr.ai.getAndIncrement()) {

                    }
                }
            }
        );
    }

     * 
     */
    public final int start, end;
    public final Thread[] threads;
    public final AtomicInteger ai;
    
    public ThreadRunner(int start, int end) {
        this(start, end, 0);
    }
    
    public ThreadRunner(int start, int end, int cpulimit) {
        this.start=start;
        this.end=end;
        this.ai= new AtomicInteger(this.start);
        int nb = getNbCpus();
        if (cpulimit>0 && nb>cpulimit) nb=cpulimit;
        this.threads = new Thread[nb];
        
    }

    public void startAndJoin() {
       startAndJoin(threads);
    }
    
    
    protected static void startAndJoin(Thread[] threads) {
        for (int ithread = 0; ithread < threads.length; ++ithread) {
            threads[ithread].setPriority(Thread.NORM_PRIORITY);
            threads[ithread].start();
            //SwingUtilities.invokeLater(threads[ithread]);
            
        }

        try {
            for (int ithread = 0; ithread < threads.length; ++ithread) {
                threads[ithread].join();
            }
        } catch (InterruptedException ie) {
            throw new RuntimeException(ie);
        }
    }
    

    public void resetAi(){
        ai.set(start);
    }
    
    private int getNbCpus() {
        return Math.max(1, Math.min(Runtime.getRuntime().availableProcessors(), end-start));
    }

    public static <T> void execute(final T[] array, final boolean setToNull, final ThreadAction<T> action) {
        if (array.length==0) return;
        if (array.length==1) {
            if (action instanceof ThreadAction2) ((ThreadAction2)action).setUp();
            action.run(array[0], 0);
            if (action instanceof ThreadAction2) ((ThreadAction2)action).tearDown();
            return;
        }
        final ThreadRunner tr = new ThreadRunner(0, array.length, 0);
        for (int i = 0; i<tr.threads.length; i++) {
            //final ThreadAction<T> localAction = action
            tr.threads[i] = new Thread(
                new Runnable() {
                    public void run() { 
                        if (action instanceof ThreadAction2) ((ThreadAction2)action).setUp();
                        for (int idx = tr.ai.getAndIncrement(); idx<tr.end; idx = tr.ai.getAndIncrement()) {
                            action.run(array[idx], idx);
                            if (setToNull) array[idx]=null;
                        }
                        if (action instanceof ThreadAction2) ((ThreadAction2)action).tearDown();
                    }
                }
            );
        }
        tr.startAndJoin();
    }
    public static <T> void execute(final List<T> array, final ThreadAction<T> action) {
        if (array.isEmpty()) return;
        if (array.size()==1) {
            if (action instanceof ThreadAction2) ((ThreadAction2)action).setUp();
            action.run(array.get(0), 0);
            if (action instanceof ThreadAction2) ((ThreadAction2)action).tearDown();
            return;
        }
        final ThreadRunner tr = new ThreadRunner(0, array.size(), 0);
        for (int i = 0; i<tr.threads.length; i++) {
            //final ThreadAction<T> localAction = action
            tr.threads[i] = new Thread(
                new Runnable() {
                    public void run() { 
                        if (action instanceof ThreadAction2) ((ThreadAction2)action).setUp();
                        for (int idx = tr.ai.getAndIncrement(); idx<tr.end; idx = tr.ai.getAndIncrement()) {
                            action.run(array.get(idx), idx);
                        }
                        if (action instanceof ThreadAction2) ((ThreadAction2)action).tearDown();
                    }
                }
            );
        }
        tr.startAndJoin();
    }
    
    public static interface ThreadAction<T> {
        public void run(T object, int idx);
    }
    public static interface ThreadAction2<T> extends ThreadAction<T> {
        public void setUp();
        public void tearDown();
    }
}
