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
package configuration.parameters;

import configuration.parameters.ui.FileChooserUI;
import configuration.parameters.ui.ParameterUI;
import java.io.File;
import java.util.Arrays;
import javax.swing.JFileChooser;
import de.caluga.morphium.annotations.Transient;
import org.json.simple.JSONArray;
import utils.JSONUtils;
import utils.Utils;

/**
 *
 * @author jollion
 */
public class FileChooser extends SimpleParameter implements Listenable {
    protected String[] selectedFiles=new String[0];
    @Transient protected FileChooserOption option = FileChooserOption.DIRECTORIES_ONLY;
    @Transient FileChooserUI ui;
    
    
    public FileChooser(String name, FileChooserOption option) {
        super(name);
        this.option=option;
    }
    
    public ParameterUI getUI() {
        if (ui==null) ui= new FileChooserUI(this);
        return ui;
    }
    
    public String[] getSelectedFilePath() {
        return selectedFiles;
    }
    
    public String getFirstSelectedFilePath() {
        if (selectedFiles.length==0) return null;
        return selectedFiles[0];
    }
    
    public void setSelectedFilePath(String... filePath) {
        if (filePath==null) selectedFiles = new String[0];
        else selectedFiles=filePath;
        fireListeners();
    }
    public void setSelectedFiles(File... filePath ) {
        selectedFiles = new String[filePath.length];
        int i = 0;
        for (File f : filePath) selectedFiles[i++]=f.getAbsolutePath();
        fireListeners();
    }
    @Override
    public boolean sameContent(Parameter other) {
        if (other instanceof FileChooser) {
            if (((FileChooser)other).selectedFiles.length==selectedFiles.length) {
                for (int i =0; i<selectedFiles.length; i++) { 
                    if ((selectedFiles[i] ==null && ((FileChooser)other).selectedFiles[i]!=null) || ( selectedFiles[i] !=null && !selectedFiles[i].equals(((FileChooser)other).selectedFiles[i]))) {
                        logger.debug("FileChoose {}!={}: difference in selected files : {} vs {}", this, other, selectedFiles, ((FileChooser)other).selectedFiles);
                        return false;
                    }
                }
                return true;
            } else {
                logger.debug("FileChoose {}!={}: # of files : {} vs {}", this, other, selectedFiles.length, ((FileChooser)other).selectedFiles.length);
                return false;
            }
        } else return false;
    }
    @Override
    public void setContentFrom(Parameter other) {
        if (other instanceof FileChooser) {
            //this.option=((FileChooser)other).option;
            this.selectedFiles=Arrays.copyOf(((FileChooser)other).selectedFiles, ((FileChooser)other).selectedFiles.length);
        } else throw new IllegalArgumentException("wrong parameter type");
    }
    
    @Override public String toString() {return name+" :"+Utils.getStringArrayAsString(selectedFiles);}
    
    public FileChooserOption getOption() {return option;}
    
    @Override public FileChooser duplicate() {
        return new FileChooser(name, option);
    }

    @Override
    public Object toJSONEntry() {
        return JSONUtils.toJSONArray(selectedFiles);
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        selectedFiles = JSONUtils.fromStringArray((JSONArray)jsonEntry);
    }
    
    public enum FileChooserOption {
        DIRECTORIES_ONLY(JFileChooser.DIRECTORIES_ONLY, false), 
        FILES_ONLY(JFileChooser.FILES_ONLY, true),
        FILES_AND_DIRECTORIES(JFileChooser.FILES_AND_DIRECTORIES, true),
        FILE_OR_DIRECTORY(JFileChooser.FILES_AND_DIRECTORIES, false);
        private final int option;
        private final boolean multipleSelection;
        FileChooserOption(int option, boolean multipleSelection) {
            this.option=option;
            this.multipleSelection=multipleSelection;
        }
        public int getOption() {return option;}
        public boolean getMultipleSelectionEnabled(){return multipleSelection;}
    }
}
