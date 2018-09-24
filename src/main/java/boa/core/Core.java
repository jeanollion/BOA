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
package boa.core;

import boa.plugins.PluginFactory;
import net.imagej.ImageJ;
import net.imagej.ops.OpService;

/**
 *
 * @author Jean Ollion
 */
public class Core {
    private static ImageJ ij;
    private static OpService opService;
    private static Core core;
    private static Object lock = new Object();
    public static Core getCore() {
        if (core==null) {
            synchronized(lock) {
                if (core==null) {
                    core = new Core();
                }
            }
        }
        return core;
    }
    private Core() {
        initIJ2();
        PluginFactory.findPlugins("boa.plugins.plugins", false);
        PluginFactory.importIJ1Plugins();
    }
    
    private static void initIJ2() {
        ij = new ImageJ();
        opService = ij.op();
    }
    public static OpService getOpService() {
        return opService;
    }
    // TODO: thread safe -> init starting GUI or console or plugin ? 
}
