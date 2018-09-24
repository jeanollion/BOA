/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.ui.logger;

import static boa.core.TaskRunner.logger;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.util.logging.Level;
import java.util.logging.Logger;
import boa.utils.FileIO;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class ConsoleProgressLogger implements ProgressLogger {
    
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
