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
package boa.gui.objects;

import boa.configuration.experiment.Experiment;
import boa.configuration.experiment.Structure;
import static boa.gui.GUI.logger;
import boa.gui.configuration.TransparentTreeCellRenderer;
import static java.awt.Component.TOP_ALIGNMENT;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.swing.JPopupMenu;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

/**
 *
 * @author Jean Ollion
 */
public class StructureSelectorTree {
    final Map<String, Integer> structureNamesMapIdx = new HashMap<>();
    Experiment xp;
    final IntConsumer callBack;
    DefaultTreeModel treeModel;
    JTree tree;
    public StructureSelectorTree(Experiment xp, IntConsumer callBack) {
        this.callBack=callBack;
        setExperiment(xp);
    }
    
    private void setExperiment(Experiment xp) {
        this.xp = xp;
        structureNamesMapIdx.clear();
        for (int sIdx = 0; sIdx<xp.getStructureCount(); sIdx++) {
            Structure s = xp.getStructure(sIdx);
            structureNamesMapIdx.put(s.getName(), sIdx);
        }
        structureNamesMapIdx.put("Root", -1);
        generateTree();
    }
    
    public void selectStructure(int structureIdx) {
       String[] path = Arrays.stream(xp.getPathToRoot(structureIdx)).mapToObj(i->xp.getStructure(i).getName()).toArray(i->new String[i]);
       DefaultMutableTreeNode[] nPath = new DefaultMutableTreeNode[path.length+1];
       
       DefaultMutableTreeNode currentNode= (DefaultMutableTreeNode)treeModel.getRoot();
       nPath[0] = currentNode;
       for (int i = 0; i<path.length; ++i) {
           int ii = i;
           nPath[i+1] = IntStream.range(0, currentNode.getChildCount())
               .mapToObj(sIdx -> (DefaultMutableTreeNode)currentNode.getChildAt(sIdx))
               .filter(n -> ((String)n.getUserObject()).equals(path[ii])).findFirst().get();
       }
       tree.setSelectionPath(new TreePath(nPath));
    }
    
    private JTree generateTree() {
        IntFunction<List<DefaultMutableTreeNode>> getDirectChildren = sIdx -> IntStream.range(sIdx+1, xp.getStructureCount()).mapToObj(s->xp.getStructure(s)).filter(s->s.getParentStructure()==sIdx).map(s->new DefaultMutableTreeNode(s.getName())).collect(Collectors.toList());
        Function<List<DefaultMutableTreeNode>, List<DefaultMutableTreeNode>> getNextLevel = parentList -> parentList.stream().flatMap(p -> {
            if (p==null) return Stream.empty();
            int sIdx = structureNamesMapIdx.get((String)p.getUserObject());
            List<DefaultMutableTreeNode> directC = getDirectChildren.apply(sIdx);
            directC.forEach(p::add);
            return directC.stream();
        }).collect(Collectors.toList());
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("Root");
        List<DefaultMutableTreeNode> nextLevel = getNextLevel.apply(new ArrayList<DefaultMutableTreeNode>(){{add(root);}});
        while (!nextLevel.isEmpty())  nextLevel = getNextLevel.apply(nextLevel);
        treeModel = new DefaultTreeModel(root);
        tree=new JTree(treeModel);
        //tree.setAlignmentY(TOP_ALIGNMENT);
        tree.setRootVisible(false);
        tree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        //tree.setOpaque(false);
        tree.setCellRenderer(new TransparentTreeCellRenderer());
        tree.setScrollsOnExpand(true);
        
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path==null) return;
                if (SwingUtilities.isLeftMouseButton(e)) { 
                    String struct = (String)((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
                    callBack.accept(structureNamesMapIdx.get(struct));
                }
            }
        });
        for (int i = 0; i<tree.getRowCount(); ++i) tree.expandRow(i);
        tree.updateUI();
        return tree;
    }
    public JTree getTree() {
        return tree;
    }
}
