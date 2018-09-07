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
package boa.configuration.experiment;

import boa.configuration.experiment.Experiment.IMPORT_METHOD;
import static boa.configuration.experiment.Experiment.IMPORT_METHOD.ONE_FILE_PER_CHANNEL_POSITION;
import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.ParameterUtils;
import boa.configuration.parameters.ContainerParameterImpl;
import boa.configuration.parameters.TextParameter;
import boa.configuration.parameters.ui.NameEditorUI;
import boa.configuration.parameters.ui.ParameterUI;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import org.json.simple.JSONObject;

/**
 *
 * @author jollion
 */
public class ChannelImage extends ContainerParameterImpl<ChannelImage> {
    NameEditorUI ui;
    Predicate<TextParameter> kwValid = (p) -> {
        Experiment xp = ParameterUtils.getExperiment(this);
        if (xp!=null) {
            IMPORT_METHOD method = xp.getImportImageMethod();
            if (IMPORT_METHOD.SINGLE_FILE.equals(method)) return true;
            if (xp.getChannelImageCount()>1 && method==IMPORT_METHOD.ONE_FILE_PER_CHANNEL_POSITION && this.getParent().getIndex(this)==0 && this.getImportImageChannelKeyword().length()==0) return false; // first must be non null if there are several channels
            long distinctKW = xp.getChannelImages().getChildren().stream().map(c -> c.getImportImageChannelKeyword()).distinct().count();
            return distinctKW == xp.getChannelImageCount();
        } else return true;
    };
    TextParameter importKeyWord = new TextParameter("Channel keyword", "", true).addValidationFunction(kwValid).setToolTipText("Keyword allowing to distinguish the file containing channel during image import, when dataset is composed of several files per position. <br />"
            + "For a given position, the name of the file containing the channel image must contain this keyword and all the files from the same position must differ only by this keyword (and eventually by frame number if each frame is in a separate file). "
            + "<br />First channel must have a non-null keyword is import method is <em>"+ONE_FILE_PER_CHANNEL_POSITION.getMethod()+"</em> and that there are several channels. "
            + "<br />All keywords should be distinct");
    
    public ChannelImage(String name) {
        super(name);
    }
    
    public ChannelImage(String name, String keyword) {
        this(name);
        setImportImageChannelKeyword(keyword);
    }
    
    public String getImportImageChannelKeyword() {return importKeyWord.getValue();}
    public void setImportImageChannelKeyword(String keyword) {importKeyWord.setValue(keyword);}
    
    @Override
    protected void initChildList() {
        super.initChildren(importKeyWord);
    }
    @Override 
    public boolean isEmphasized() {
        return false;
    }
    @Override
    public ParameterUI getUI() {
        if (ui==null) ui=new NameEditorUI(this, false);
        return ui;
    }
    @Override
    public Object toJSONEntry() {
        JSONObject res = new JSONObject();
        res.put("name", name);
        res.put("importKeyword", this.importKeyWord.toJSONEntry());
        return res;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
        JSONObject jsonO = (JSONObject)jsonEntry;
        name = (String)jsonO.get("name");
        importKeyWord.initFromJSONEntry(jsonO.get("importKeyword"));
    }
    
}
