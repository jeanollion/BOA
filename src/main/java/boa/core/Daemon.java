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
package boa.core;

import boa.ui.ConsoleUserInterface;
import static boa.gui.GUI.log;
import boa.ui.LogUserInterface;
import boa.ui.UserInterface;
import com.google.common.io.Files;
import boa.core.DefaultWorker.WorkerTask;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.json.simple.JSONObject;
import boa.utils.FileIO;
import boa.utils.HashMapGetCreate;
import boa.utils.JSONUtils;

/**
 *
 * @author jollion
 */
public class Daemon {
    UserInterface ui;
    LogUserInterface logUI;
    String watchDir;
    File parsedJobDir, errorDir, logDir;
    boolean watching, running;
    long idleTime = 10000;
    DefaultWorker w;
    final Queue<Task> jobQueue = new LinkedList<>();
    final Map<Task, File> taskFileNameMap = new HashMap<>();
    final Map<File, Boolean> fileNameErrorMap = new HashMap<>();
    final Set<File> oneJobHasBeenRun = new HashSet<>();
    public Daemon(UserInterface ui) {
        this.ui=ui;
    }

    public void terminateWatch() {
        watching = false;
        ui.setMessage("Stop Watching");
    }
    public void stopAfterNextJob() {
        running = false;
        watching = false;
        ui.setMessage("Stop Running");
    }
    public void terminateNow() {
        if (w!=null) w.cancel(true);
    }
    public void watchDirectory(String dir) {
        File wD = new File(dir);
        if (!wD.isDirectory()) {
            ui.setMessage("Cannot set watch directory: "+dir);
            return;
        }
        File pd = new File(dir+File.separator+"ParsedJobs");
        File ed = new File(dir+File.separator+"Errors");
        File ld = new File(dir+File.separator+"Logs");
        pd.mkdirs();
        ed.mkdirs();
        ld.mkdirs();
        if (!pd.isDirectory() || !ed.isDirectory() || !ld.isDirectory()) {
            ui.setMessage("Cannot create sub directories within directory: "+dir);
            return;
        }
        watchDir=dir;
        parsedJobDir=pd;
        errorDir=ed;
        logDir=ld;
        watching = true;
        ui.setMessage("Setting watch directory: "+dir);
        WorkerTask wt = new WorkerTask() {
            @Override
            public String run(int i) {
                while(running) {
                    Task t = jobQueue.poll();
                    if (t!=null) {
                        logUI.setAppend(!oneJobHasBeenRun.contains(taskFileNameMap.get(t)));
                        logUI.setLogFile(logDir.getAbsolutePath()+File.separator+taskFileNameMap.get(t).getName().replace(".json", ".txt"));
                        t.publishMemoryUsage("");
                        ui.setMessage("Running Job: "+t+" remaining jobs: "+jobQueue.size());
                        t.runTask();
                        t.publishErrors();
                        if (!t.errors.isEmpty()) fileNameErrorMap.put(taskFileNameMap.get(t), true);
                        logUI.setLogFile(null);
                        oneJobHasBeenRun.add(taskFileNameMap.get(t));
                        taskFileNameMap.remove(t);
                        moveFiles();
                    }
                    else if (watching) {
                        addFiles(watchDir);
                        while(watching && jobQueue.isEmpty()) {
                            try {
                                Thread.sleep(idleTime);
                            } catch (InterruptedException ex) {}
                            addFiles(watchDir);
                        }
                    } else running=false;
                }
                return "";
            }
        };
        running = true;
        w = DefaultWorker.execute(wt, 1, null);
    }
    public void addFiles(String dir) {
        File[] subF = new File(dir).listFiles(f->f.isFile() && f.getName().endsWith(".json"));
        if (subF==null || subF.length==0) {
            //ui.setMessage("no job found in directory: "+dir);
            return;
        }
        for (File f : subF) {
            List<String> jobs = FileIO.readFromFile(f.getAbsolutePath(), s->s);
            boolean error= false;
            for (String s : jobs) {
                JSONObject o = JSONUtils.parse(s);
                if (o==null) {
                    ui.setMessage("Error: could not parse task: "+s);
                    error = true;
                }
                else {
                    Task t = new Task().fromJSON(o).setUI(ui);
                    if (t.isValid()) {
                        jobQueue.add(t);
                        taskFileNameMap.put(t, f);
                    }
                    else error=true;
                }
                fileNameErrorMap.put(f, error);
            }
        }
        ui.setMessage(jobQueue.size()+ " new jobs found in directory: "+dir);
    }
    private void moveFiles() {
        Iterator<Entry<File, Boolean>> it = fileNameErrorMap.entrySet().iterator();
        while(it.hasNext()) {
            Entry<File, Boolean> e = it.next();
            if (!taskFileNameMap.containsValue(e.getKey())) { // all jobs are done
                try { // move file to subfolder so that it is not scanned again
                    Files.move(e.getKey(), new File((e.getValue()?errorDir.getAbsolutePath():parsedJobDir.getAbsolutePath())+File.separator+e.getKey().getName()));
                } catch (IOException ex) {
                    ui.setMessage("Cannot move file: "+e.getKey().getName() + " "+ ex.getMessage());
                }
            }
        }
        
    }
}
