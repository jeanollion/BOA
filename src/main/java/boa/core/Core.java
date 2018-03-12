/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.core;

import net.imagej.ImageJ;
import net.imagej.ops.OpService;

/**
 *
 * @author jollion
 */
public class Core {
    private static ImageJ ij;
    private static OpService opService;
    private static Core core;
    private Core() { }
    
    private static void initIJ2() {
        ij = new ImageJ();
        opService = ij.op();
    }
    public static OpService getOpService() {
        if (opService==null) initIJ2();
        return opService;
    }
    // TODO: thread safe -> init starting GUI or console or plugin ? 
}
