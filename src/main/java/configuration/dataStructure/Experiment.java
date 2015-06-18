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
package configuration.dataStructure;

import configuration.parameters.ChoiceParameter;
import configuration.parameters.Parameter;
import configuration.parameters.SimpleContainerParameter;
import configuration.parameters.SimpleListParameter;
import configuration.userInterface.ConfigurationTreeModel;
import configuration.userInterface.TreeModelContainer;
import java.awt.Component;
import java.util.ArrayList;
import java.util.Enumeration;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.PostLoad;
import org.mongodb.morphia.annotations.Transient;

/**
 *
 * @author jollion
 * @param <Structure>
 */

@Entity(value = "Experiment", noClassnameStored = true)
public class Experiment extends SimpleContainerParameter implements TreeModelContainer {
    ChoiceParameter choice;
    StructureList structures;
    @Transient ConfigurationTreeModel model;
    
    public Experiment(String name) {
        super(name);
        choice = new ChoiceParameter("Choice name", new String[]{"choice 1", "choice2"}, "choice 1");
        structures = new StructureList();
        init(choice, structures);
    }
    
    private void init(Parameter... parameters) { 
        super.initChildren(parameters);
    }

    @Override
    public ConfigurationTreeModel getModel() {
        return model;
    }

    @Override
    public void setModel(ConfigurationTreeModel model) {
        this.model=model;
    }
    
    // morphia
    private Experiment(){}
    
    @PostLoad void postLoad() {super.initChildren(choice, structures);}

    
    
}
