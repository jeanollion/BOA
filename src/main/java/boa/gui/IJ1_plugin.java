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
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ij.plugin.PlugIn;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;
import loci.common.DebugTools;
import loci.formats.FormatHandler;
import org.apache.log4j.LogManager;
import org.scijava.ui.swing.options.OptionsLookAndFeel;
import org.slf4j.LoggerFactory;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class IJ1_plugin implements PlugIn {
    @Override
    public void run(String string) {
        
        String lookAndFeel = null;
        
        Map<String, LookAndFeelInfo> lafMap = Arrays.asList(UIManager.getInstalledLookAndFeels()).stream().collect(Collectors.toMap(LookAndFeelInfo::getName, Function.identity()));
        logger.info("LookAndFeels {}", lafMap.keySet());
        if (lafMap.keySet().contains("Quaqua")) lookAndFeel="Quaqua";
        else if (lafMap.keySet().contains("Seaglass")) lookAndFeel="Seaglass";
        else if (lafMap.keySet().contains("Nimbus")) lookAndFeel="Nimbus";
        else if (lafMap.keySet().contains("Metal")) lookAndFeel="Metal";
        /*
        String OS_NAME = System.getProperty("os.name");
        if ("Linux".equals(OS_NAME)) {
            if (uiNames.contains("Nimbus")) lookAndFeel="Nimbus";
        }*/
        if (lookAndFeel!=null) {
            logger.info("set LookAndFeel: {}", lookAndFeel);
            try {
                // Set cross-platform Java L&F (also called "Metal")
                UIManager.setLookAndFeel( lafMap.get(lookAndFeel).getClassName());
            } 
            catch (UnsupportedLookAndFeelException e) {
               // handle exception
            }
            catch (ClassNotFoundException e) {
               // handle exception
            }
            catch (InstantiationException e) {
               // handle exception
            }
            catch (IllegalAccessException e) {
               // handle exception
            }
        }
        
        /*
        
        */
        //System.setProperty("scijava.log.level", "error");
        //DebugTools.enableIJLogging(false);
        //DebugTools.enableLogging("ERROR");
        ((Logger)LoggerFactory.getLogger(FormatHandler.class)).setLevel(Level.OFF);
        System.setProperty("scijava.log.level", "error");
        
        
        // TODO find other IJ1&2 plugins & ops...
        
        new GUI().setVisible(true);
        
    }
    
}
