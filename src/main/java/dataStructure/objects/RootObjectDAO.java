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
package dataStructure.objects;

import dataStructure.configuration.Experiment;
import dataStructure.containers.MultipleImageContainerSingleFile;
import dataStructure.objects.StructureObjectRoot;
import de.caluga.morphium.DAO;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.query.Query;
import java.util.Arrays;
import org.bson.types.ObjectId;

/**
 *
 * @author jollion
 */
public class RootObjectDAO extends DAO<StructureObjectRoot>{
    Morphium morphium;
    public RootObjectDAO(Morphium morphium) {
        super(morphium, StructureObjectRoot.class);
        morphium.ensureIndicesFor(StructureObjectRoot.class);
        this.morphium=morphium;
    }
    public void store(StructureObjectRoot o) {
        morphium.store(o);
        updateNextId(o);
    }
    public void delete(StructureObjectRoot o) {
        morphium.delete(o);
    }
    public void store(StructureObjectRoot[] objects) {
        for (StructureObjectRoot o : objects) morphium.store(o);
        for (StructureObjectRoot o : objects) updateNextId(o);
    }
    
    private Query<StructureObjectRoot> getQuery(String name, int timePoint) {
        return super.getQuery().f("name").eq(name).f("time_point").eq(timePoint);
    }
    
    public StructureObjectRoot getObject(ObjectId id) {//, Experiment xp, MultipleImageContainerSingleFile preProcessedImages, ObjectDAO objectDAO) {
        StructureObjectRoot root =  super.getQuery().getById(id); // FIXEME: setup??
        //if (root==null) System.out.println("object root:"+id+ " not found!");
        //root.setUp(xp, preProcessedImages, this, objectDAO);
        return root;
    }
    
    public StructureObjectRoot getRoot(String name, int timePoint, Experiment xp, ObjectDAO objectDAO) {
        StructureObjectRoot root= getQuery(name, timePoint).get();
        root.setUp(xp, this, objectDAO);
        return root;
    }
    
    private void updateNextId(StructureObjectRoot o) {
        if (o.next != null && o.nextId == null && o.next.id != null) {
            o.nextId = o.next.id;
            morphium.updateUsingFields(o, "next_id");
        }
    }
    
    public void updateTrackLinks(StructureObjectAbstract o) {
        boolean prev = o.previous!=null && o.previousId==null && o.previous.id!=null;
        boolean next = o.next!=null && o.nextId==null && o.next.id!=null;
        if (prev) o.previousId=o.previous.id;
        if (next) o.nextId=o.next.id;
        if (prev && next) morphium.updateUsingFields(o, "next_id", "previous_id");
        else if (prev) morphium.updateUsingFields(o, "previous_id");
        else if (next) morphium.updateUsingFields(o, "next_id");
    }
    
}
