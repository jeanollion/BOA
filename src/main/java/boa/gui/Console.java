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
package boa.gui;

import com.mongodb.util.JSON;
import core.Task;
import ij.plugin.PlugIn;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.json.simple.JSONObject;
import utils.FileIO;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class Console implements UserInterface, PlugIn {
    public static UserInterface ui;
    
    @Override
    public void run(String args) {
        setMessage("BOA Shell version: "+Utils.getVersion(this));
        if (args==null || args.length()==0) return;
        ui = this;
        Collection<Task> jobs;
        Function<String, Task> parser = s->new Task().fromJSON((JSONObject)JSON.parse(s));
        if (args.endsWith(".txt")|args.endsWith(".json")) { // open text file
            jobs = FileIO.readFromFile(args, parser); // TODO fromJSON
        } else { // directly parse command
            String[] tasksS = args.split("\n");
            String[] tasksS2 = args.split(";");
            if (tasksS2.length>tasksS.length) tasksS=tasksS2;
            jobs = new HashSet<>(tasksS.length);
            for (String s : tasksS) jobs.add(parser.apply(s));
        }
        runJobs(jobs);
    }
    public void runJobs(Collection<Task> jobs) {
        if (jobs==null || jobs.isEmpty()) return;
        for (Task t : jobs) {
            if (!t.isValid()) {
                setMessage("Error: job: "+t.toString()+" is not valid" + (t.getDB()==null?"db null": (t.getDB().getExperiment()==null? "xp null":"")));
                return;
            }
        }
        System.out.println(">Will execute: "+jobs.size()+" jobs");
        for (Task t : jobs) {
            setMessage("Running: "+t.toString());
            t.runTask();
            
        }
        int errorCount = 0;
        for (Task t: jobs) errorCount+=t.getErrors().size();
        setMessage("All jobs finished. Errors: "+errorCount);
        for (Task t: jobs) t.printErrors();
    }
    
    private String prompt(String promptInstruction) {
        if (promptInstruction!=null && promptInstruction.length()>0) System.out.println(promptInstruction);
        setMessage(">");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
           return(br.readLine());
        } catch (IOException ioe) {
           setMessage("IO error trying to read command!");
        }
        return "";
    }
    
    private boolean promptBool(String instruction) {
        String p = prompt(instruction+" [Y/N]:");
        if ("Y".equals(p)) return true;
        else return false;
    }

    @Override
    public void setProgress(int i) {
        setMessage("Progress: "+i+"%");
    }

    @Override
    public void setMessage(String message) {
        System.out.println(">"+message);
    }

    @Override
    public void setRunning(boolean running) {
        
    }
}
