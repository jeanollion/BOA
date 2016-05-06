/*
 * Copyright (C) 2016 jollion
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

import static boa.gui.GUI.logger;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jollion
 */
public class PropertyUtils {
    private static Properties props;
    public final static String MONGO_BIN_PATH = "mongo_bin_path";
    public final static String LAST_SELECTED_EXPERIMENT = "last_selected_xp";
    public final static String EXPORT_FORMAT = "export_format";
    public final static String LAST_IMPORT_IMAGE_DIR = "last_import_image_dir";
    public final static String LAST_IO_DATA_DIR = "last_io_data_dir";
    public static Properties getProps() { 
        if (props == null) { 
            props = new Properties();  
            File f = getFile(); 
            if (f.exists()) { 
                try { 
                    props.load(new FileReader(f)); 
                } catch (IOException e) { 
                    logger.error("Error while trying to load property file", e);
                } 
            } 
        }
        
        return props; 
    }
    public static String get(String key) {
        return getProps().getProperty(key);
    }
    public static String get(String key, String defaultValue) {
        return getProps().getProperty(key, defaultValue);
    }
    public static void set(String key, String value) {
        getProps().setProperty(key, value);
        saveParamChanges();
    }
    
    public static synchronized void saveParamChanges() {
        try {
            File f = getFile();
            OutputStream out = new FileOutputStream( f );
            props.store(out, "This is an optional header comment string");
        }
        catch (Exception e ) {
            logger.error("Error while trying to save to property file", e);
        }
    }
    
    private static File getFile() { 
        String path = System.getProperty("user.home") + "/.boa.cfg";
        File f = new File(path); 
        if (!f.exists()) try {
            f.createNewFile();
        } catch (IOException ex) {
            logger.error("Error while trying to create property file at: "+path, ex);
        }
        return f;
    }
    
}
