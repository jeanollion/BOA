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
package dataStructure.objects;

import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import static dataStructure.objects.StructureObject.logger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jollion
 */
public class ObjectStoreAgent {
    final LinkedBlockingQueue<Job> queue;
    final ObjectDAO dao;
    Thread thread;
    public ObjectStoreAgent(ObjectDAO dao) {
        this.dao = dao;
        createThread();
        queue = new LinkedBlockingQueue<Job>();
    }
    
    private void createThread() {
        thread = new Thread(new Runnable() {

            public void run() {
                while (!queue.isEmpty()) executeJob(queue.poll());
                logger.debug("StoreAgent: no more jobs to run");
            }
        });
    }
    
    private void executeJob(Job job) {
        long tStart = System.currentTimeMillis();
        dao.storeNow(job.objects, job.updateTrackLinks);
        long tEnd = System.currentTimeMillis();
        logger.debug("Job done: {} objects stored in {} ms", job.objects.size(), tEnd-tStart);
    }
    
    public synchronized void addJob(List<StructureObject> list, boolean updateTrackLinks) {
        Job job = new Job(list, updateTrackLinks);
        queue.add(job);
        if (thread==null || !thread.isAlive()) {
            
            logger.debug("StoreAgent: thread was not running, running thread.. state: {}, isInterrupted: {}", thread!=null?thread.getState():"null", thread!=null?thread.isInterrupted():null);
            createThread();
            thread.start();
            
        } else logger.debug("StoreAgent: thread was already running..,  state: {}, isInterrupted: {}", thread!=null?thread.getState():"null", thread!=null?thread.isInterrupted():null);        
    }
    public void join() {
        if (thread!=null) try {
            thread.join();
        } catch (InterruptedException ex) {
            logger.debug("ObjectStoreAgent Error: join thread", ex);
        }
    }
    
    private class Job {
        List<StructureObject> objects;
        boolean updateTrackLinks;
        public Job(List<StructureObject> list, boolean updateTrackLinks) {
            this.objects=list;
            this.updateTrackLinks=updateTrackLinks;
        }
    }
}
