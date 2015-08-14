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

import java.util.Collections;
import loci.common.DebugTools;
import org.apache.log4j.Level;



/**
 *
 * @author jollion
 */
public class Core {
    
    private static final Core INSTANCE = new Core();
    public static Core getInstance() {return INSTANCE;}
    private Core(){
        System.out.println("Init Core...");
        /*LogManager.getLogger("org.mongodb.driver.connection").setLevel(org.apache.log4j.Level.OFF);
        LogManager.getLogger("org.mongodb.driver.management").setLevel(org.apache.log4j.Level.OFF);
        LogManager.getLogger("org.mongodb.driver.cluster").setLevel(org.apache.log4j.Level.OFF);
        LogManager.getLogger("org.mongodb.driver.protocol.insert").setLevel(org.apache.log4j.Level.OFF);
        LogManager.getLogger("org.mongodb.driver.protocol.query").setLevel(org.apache.log4j.Level.OFF);
        LogManager.getLogger("org.mongodb.driver.protocol.update").setLevel(org.apache.log4j.Level.OFF);
        LogManager.getLogger("loci.formats").setLevel(org.apache.log4j.Level.OFF);
        LogManager.getLogger("loci.common").setLevel(org.apache.log4j.Level.OFF);
        LogManager.getLogger("loci.common.Location").setLevel(Level.OFF);
        LogManager.getLogger("loci.formats.in.MinimalTiffReader").setLevel(Level.OFF);
        LogManager.getLogger("loci.formats.FormatHandler").setLevel(Level.OFF);
        LogManager.getLogger("loci.formats.ImageReader").setLevel(Level.OFF);
        LogManager.getLogger("loci.formats.in.TiffReader").setLevel(Level.OFF);
        LogManager.getLogger("loci.formats.in.BaseTiffReader").setLevel(Level.OFF);*/
        java.util.logging.Logger mongoLogger = java.util.logging.Logger.getLogger("com.mongodb" );
        mongoLogger.setLevel(java.util.logging.Level.SEVERE);
        //DebugTools.enableLogging("ERROR");
    }
}
