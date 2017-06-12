/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package utils;
import static core.Processor.logger;
import core.ProgressCallback;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
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
    tr.startAndJoin();

     * 
     */
    public final int start, end;
    public final Thread[] threads;
    public final AtomicInteger ai;
    public final List<Pair<String, Exception>> errors = new ArrayList<>();
    public ThreadRunner(int start, int end) {
        this(start, end, 0);
    }
    
    public int size() {
        return this.threads.length;
    }
    public static boolean leaveOneCPUFree = true;
    /**
     * 
     * @param start inclusive
     * @param end exclusive 
     * @param cpulimit 
     */
    public ThreadRunner(int start, int end, int cpulimit) {
        this.start=start;
        this.end=end;
        this.ai= new AtomicInteger(this.start);
        int nb = getNbCpus();
        if (cpulimit>0 && nb>cpulimit) {
            nb=cpulimit;
            if (leaveOneCPUFree && nb==getNbCpus() && nb>1) nb--;
        }
       
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
        return Math.max(1, Math.min(getMaxCPUs(), end-start));
    }
    
    public static int getMaxCPUs() {
        return Runtime.getRuntime().availableProcessors();
    }
    public static <T> List<Pair<String, Exception>> execute(final T[] array, final boolean setToNull, final ThreadAction<T> action) {
        return execute(array, setToNull, action, null, null);
    }
    public static <T> List<Pair<String, Exception>> execute(final T[] array, final boolean setToNull, final ThreadAction<T> action, ProgressCallback pcb) {
        return execute(array, setToNull, action, null, pcb);
    }
    
    public static <T> List<Pair<String, Exception>> execute(T[] array, final boolean setToNull, final ThreadAction<T> action, ExecutorService executor, ProgressCallback pcb) {
        if (array==null) return Collections.EMPTY_LIST;
        if (array.length==0) return Collections.EMPTY_LIST;
        if (array.length==1) {
            List<Pair<String, Exception>> errors = new ArrayList<>(1);
            try {
                action.run(array[0], 0);
            } catch (Exception e) {
                errors.add(new Pair(array[0].toString(), e));
            }
            return errors;
        }
        if (executor==null) executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        CompletionService<Pair<String, Exception>> completion = new ExecutorCompletionService<>(executor);
        final List<Pair<String, Exception>> errors = new ArrayList<>();
        int idx=0;
        for (T e : array) {
            final int i = idx;
            completion.submit(()->{
                try {
                    action.run(e, i);
                } catch (Exception ex) {
                    return new Pair(e.toString(), ex);
                } finally {
                    return null;
                }
            });
            if (setToNull) array[idx]=null;
            ++idx;
        }
        if (pcb!=null) pcb.incrementTaskNumber(idx);
        for (int i = 0; i<idx; ++i) {
            Pair<String, Exception> e;
            try {
                e = completion.take().get();
                if (e!=null) errors.add(e);
            } catch (InterruptedException|ExecutionException ex) {
                errors.add(new Pair("Execution exception", ex));
            }
            if (pcb!=null) pcb.incrementProgress();
        }
        return errors;
    }
    public static <T> List<Pair<String, Exception>> execute(Collection<T> array, boolean removeElements, final ThreadAction<T> action) {
        return execute(array, removeElements, action, null, null);
    }
    public static <T> List<Pair<String, Exception>> execute(Collection<T> array, boolean removeElements, final ThreadAction<T> action, ProgressCallback pcb) {
        return execute(array, removeElements, action, null, pcb);
    }
    public static <T> List<Pair<String, Exception>> execute(Collection<T> array, boolean removeElements, final ThreadAction<T> action,  ExecutorService executor, ProgressCallback pcb) {
        if (array==null) return Collections.EMPTY_LIST;
        if (array.isEmpty()) return Collections.EMPTY_LIST;
        if (array.size()==1) {
            List<Pair<String, Exception>> errors = new ArrayList<>(1);
            T e = array.iterator().next();
            try {
                action.run(array.iterator().next(), 0);
            } catch (Exception ex) {              
                errors.add(new Pair(e.toString(), ex));           
            }
            return errors;
        }
        if (executor==null) executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        CompletionService<Pair<String, Exception>> completion = new ExecutorCompletionService<>(executor);
        final List<Pair<String, Exception>> errors = new ArrayList<>();
        int count=0;
        Iterator<T> it = array.iterator();
        while(it.hasNext()) {
            T e = it.next();
            final int i = count;
            completion.submit(()->{
                try {
                    //if (pcb!=null) pcb.log("will run process: "+i+" -> "+e);
                    action.run(e, i);
                    //if (pcb!=null) pcb.log("has run process: "+i+" -> "+e);
                } catch (Exception ex) {
                    //logger.debug("error on: "+e, ex);
                    return new Pair(e.toString(), ex);
                } 
                return null;
            });
            if (removeElements) it.remove();
            ++count;
        }
        if (pcb!=null) pcb.incrementTaskNumber(count);
        for (int i = 0; i<count; ++i) {
            Pair<String, Exception> e;
            try {
                e = completion.take().get();
                if (e!=null) errors.add(e);
            } catch (InterruptedException|ExecutionException ex) {
                errors.add(new Pair("Execution exception", ex));
            }
            if (pcb!=null) pcb.incrementProgress();
        }
        return errors;
    }
    
    public static interface ThreadAction<T> {
        public void run(T object, int idx);
    }
    
    public static ProgressCallback loggerProgressCallback(final org.slf4j.Logger logger) {
        return new ProgressCallback() {
            int subTask = 0;
            int taskCount = 0;
            @Override
            public void incrementTaskNumber(int subtask) {
                this.subTask+=subtask;
            }

            @Override
            public void incrementProgress() {
                logger.debug("Current: {}/{}", ++taskCount, subTask);
            }

            @Override
            public void log(String message) {
                logger.debug(message);
            }
        };
    }
    public static PriorityThreadFactory priorityThreadFactory(int priority) {return new PriorityThreadFactory(priority);}
    public static class PriorityThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final int priority;
        public PriorityThreadFactory(int priority) {
            if (priority<Thread.MIN_PRIORITY) priority=Thread.MIN_PRIORITY;
            if (priority>Thread.MAX_PRIORITY) priority=Thread.MAX_PRIORITY;
            this.priority=priority;
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() :
                                  Thread.currentThread().getThreadGroup();
            namePrefix = "pool-" +
                          poolNumber.getAndIncrement() +
                         "-thread-";
        }
        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r,
                                  namePrefix + threadNumber.getAndIncrement(),
                                  0);
            if (t.isDaemon())
                t.setDaemon(false);
            if (t.getPriority() != priority)
                t.setPriority(priority);
            return t;
        }
    }
}
