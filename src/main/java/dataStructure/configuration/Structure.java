/*

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
package dataStructure.configuration;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.StructureParameterParent;
import javax.swing.tree.MutableTreeNode;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.PostLoad;

/**
 *
 * @author jollion
 */
@Embedded
public class Structure extends SimpleContainerParameter {
    StructureParameterParent parentStructure =  new StructureParameterParent("Parent Structure", -1, 0);;
    ProcessingChain processingChain = new ProcessingChain("Processing Chain");
    
    public Structure(String name) {
        super(name);
        initChildList();
    }
    
    public ProcessingChain getProcessingChain() {
        return processingChain;
    }
    
    public int getParentStructure() {
        return parentStructure.getSelectedStructure();
    }
    
    @Override
    protected void initChildList() {
        super.initChildren(parentStructure, processingChain);
    }
    
    @Override 
    public void setParent(MutableTreeNode newParent) {
        super.setParent(newParent);
        parentStructure.setMaxStructure(parent.getIndex(this));
    }

    @Override
    public Structure duplicate() {
        Structure res=new Structure(name);
        res.setContentFrom(this);
        return res;
    }
    
    // morphia
    public Structure(){super(); initChildList();} // mettre dans la clase abstraite SimpleContainerParameter?
    

    
}
