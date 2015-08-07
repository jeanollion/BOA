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
package core;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


/**
 *
 * @author jollion
 */
public class Core {
    private static final Core INSTANCE = new Core();
    private static final Logger log = Logger.getLogger(Core.class.getName());
    private static Handler logHandler;
    public static Core getInstance() {return INSTANCE;}
    public static Logger getLogger(){return log;}
    private Core(){
        System.out.println("Init Core...");
        logHandler = new ConsoleHandler();
        logHandler.setFormatter(new SimpleFormatter());
        logHandler.setLevel(Level.ALL);
        log.addHandler(logHandler);
        log.log(Level.INFO, "Init Core...");
    }
}
