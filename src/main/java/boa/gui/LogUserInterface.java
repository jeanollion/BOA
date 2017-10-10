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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import utils.FileIO;

/**
 *
 * @author jollion
 */
public class LogUserInterface implements UserInterface {
    final UserInterface parentUI;
    File logFile;
    FileLock xpFileLock;
    RandomAccessFile logFileWriter;
    
    public LogUserInterface(UserInterface parentUI) {
        this.parentUI = parentUI;
    }
    public UserInterface getParentUI() {
        return parentUI;
    }
    private synchronized void lockLogFile() {
        if (xpFileLock!=null) return;
        try {
            setMessage("locking file: "+logFile.getAbsolutePath());
            if (!logFile.exists()) logFile.createNewFile();
            logFileWriter = new RandomAccessFile(logFile, "rw");
            xpFileLock = logFileWriter.getChannel().tryLock();
        } catch (FileNotFoundException ex) {
            setMessage("no config file found!");
            logFile=null;
        } catch (OverlappingFileLockException e) {
            setMessage("file already locked");
            logFile=null;
        } catch (IOException ex) {
            setMessage("File could not be locked");
            logFile=null;
        }
    }
    public synchronized void unlockLogFile() {
        if (this.xpFileLock!=null) {
            try {
                setMessage("realising lock: "+ xpFileLock);
                xpFileLock.release();
            } catch (IOException ex) {
                setMessage("error realeasing xp lock");
            } finally {
                xpFileLock = null;
            }
        }
        if (logFileWriter!=null) {
            try {
                logFileWriter.close();
            } catch (IOException ex) {
                setMessage("could not close config file");
            } finally {
                logFileWriter = null;
            }
        }
    }
    public boolean setLogFile(String dir, boolean clearIfExisting) {
        if (dir==null) {
            this.unlockLogFile();
            return true;
        }
        logFile = new File(dir);
        lockLogFile();
        if (this.logFileWriter!=null && clearIfExisting) {
            try {
                FileIO.clearRAF(logFileWriter);
            } catch (IOException ex) { }
        }
        return logFileWriter!=null;
    }
    
    @Override
    public void setProgress(int i) {
        parentUI.setProgress(i);
    }

    @Override
    public void setMessage(String message) {
        if (logFileWriter!=null) {
            try {
                FileIO.write(logFileWriter, message, true);
            } catch (IOException ex) {
                System.out.println(">cannot log to file:"+logFile.getAbsolutePath());
            }
        }
    }

    @Override
    public void setRunning(boolean running) {
        
    }
    
}
