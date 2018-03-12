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

import boa.configuration.parameters.SimpleContainerParameter;
import boa.configuration.parameters.TextParameter;
import boa.configuration.parameters.ui.NameEditorUI;
import boa.configuration.parameters.ui.ParameterUI;
import org.json.simple.JSONObject;

/**
 *
 * @author jollion
 */
public class ChannelImage extends SimpleContainerParameter {
    NameEditorUI ui;
    TextParameter importKeyWord = new TextParameter("import file channel keyword", "", true);
    
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
