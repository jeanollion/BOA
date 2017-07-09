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
public class Console implements UserInterface{
    public static UserInterface ui;
    public void run(String args) {
        ui = this;
        System.out.println(">BOA Shell version: "+Utils.getVersion(this));
        Collection<Task> jobs;
        Function<String, Task> parser = s->new Task();//(JSONObject)JSON.parse(s)
        if (args.endsWith(".txt")|args.endsWith(".json")) { // open text file
            jobs = FileIO.readFromFile(args, parser); // TODO fromJSON
        } else { // directly parse command
            String[] tasksS = args.split("/n");
            jobs = new HashSet<>(tasksS.length);
            for (String s : tasksS) jobs.add(parser.apply(s));
        }
        runJobs(jobs);
    }
    public void runJobs(Collection<Task> jobs) {
        if (jobs==null || jobs.isEmpty()) return;
        for (Task t : jobs) {
            if (!t.isValid()) {
                System.out.println(">Error: job: "+t.toString()+" is not valid" + (t.getDB()==null?"db null": (t.getDB().getExperiment()==null? "xp null":"")));
                return;
            }
        }
        System.out.println(">Will execute: "+jobs.size()+" jobs");
        for (Task t : jobs) {
            System.out.println("Running: "+t.toString());
            t.runTask();
            t.printErrors();
        }
    }
    private String prompt(String promptInstruction) {
        if (promptInstruction!=null && promptInstruction.length()>0) System.out.println(promptInstruction);
        System.out.print(">");
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        try {
           return(br.readLine());
        } catch (IOException ioe) {
           System.out.println("IO error trying to read command!");
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
        System.out.println("Progress: "+i+"%");
    }

    @Override
    public void setMessage(String message) {
        System.out.println(">"+message);
    }

    @Override
    public void setRunning(boolean running) {
        
    }
}
