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
package configuration.parameters.ui;

import configuration.parameters.FileChooser;
import configuration.parameters.ParameterUtils;
import boa.gui.configuration.ConfigurationTreeModel;
import java.io.File;
import javax.swing.JFileChooser;
import static configuration.parameters.Parameter.logger;
/**
 *
 * @author jollion
 */
public class FileChooserUI implements ParameterUI {
    FileChooser fcParam;
    String curDir;
    ConfigurationTreeModel model;
    public FileChooserUI(FileChooser fc) {
        this.fcParam=fc;
        this.model= ParameterUtils.getModel(fc);
    }
    @Override
    public Object[] getDisplayComponent() { // ouvre directement la fenêtre pour choisir des dossers
        final JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(fcParam.getOption().getOption());
        //fc.setFileHidingEnabled(false);
        fc.setMultiSelectionEnabled(fcParam.getOption().getMultipleSelectionEnabled());
        if (curDir != null) {
            fc.setCurrentDirectory(new File(curDir));
        }
        fc.setDialogTitle(fcParam.getName());
        int returnval = fc.showOpenDialog(model.getTree());
        logger.debug("file chooser: {}: returned value? {}", fcParam.getName(), returnval == JFileChooser.APPROVE_OPTION);
        if (returnval == JFileChooser.APPROVE_OPTION) {
            if (fcParam.getOption().getMultipleSelectionEnabled()) fcParam.setSelectedFiles(fc.getSelectedFiles());
            else fcParam.setSelectedFiles(fc.getSelectedFile());
            model.nodeChanged(fcParam);
        }
        return new Object[]{};
        
    }
    
    
    
    
    
}
