/*
 * Copyright (C) 2015 nasique
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
package dataStructure;

import core.Processor;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.configuration.ExperimentDAO;
import dataStructure.configuration.MicroscopyField;
import dataStructure.configuration.Structure;
import dataStructure.objects.Object3D;
import dataStructure.objects.ObjectDAO;
import dataStructure.objects.StructureObject;
import de.caluga.morphium.Morphium;
import de.caluga.morphium.MorphiumConfig;
import image.BlankMask;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static junit.framework.Assert.assertEquals;
import org.junit.Test;
import utils.MorphiumUtils;

/**
 *
 * @author nasique
 */
public class TestTrackStructure {
    @Test
    public void testTrackStructure() {
        Morphium m=MorphiumUtils.createMorphium("testTrack");
        m.clearCollection(Experiment.class);
        m.clearCollection(StructureObject.class);
        
        

        Experiment xp = new Experiment("test");
        ChannelImage image = new ChannelImage("ChannelImage");
        xp.getChannelImages().insert(image);
        xp.getStructures().removeAllElements();
        Structure microChannel = new Structure("MicroChannel", -1, 0);
        Structure bacteries = new Structure("Bacteries", 0, 0);
        xp.getStructures().insert(microChannel, bacteries);
        bacteries.setParentStructure(0);
        xp.createMicroscopyField("field1");
        ExperimentDAO xpDAO = new ExperimentDAO(m);
        xpDAO.store(xp);
        ObjectDAO dao = new ObjectDAO(m, xpDAO);
        
        StructureObject[] rootT = new StructureObject[5];
        for (int i = 0; i<rootT.length; ++i) rootT[i] = new StructureObject("field1", i, new BlankMask("", 1, 1, 1), xp);
        Processor.trackRoot(Arrays.asList(rootT));
        dao.store(true, rootT);
        StructureObject[] mcT = new StructureObject[5];
        for (int i = 0; i<mcT.length; ++i) mcT[i] = new StructureObject("field1", i, 0, 0, new Object3D(new BlankMask("", 1, 1, 1), 1), rootT[i], xp);
        Processor.trackRoot(Arrays.asList(mcT));
        dao.store(true, mcT);
        StructureObject[][] bTM = new StructureObject[5][3];
        for (int t = 0; t<bTM.length; ++t) {
            for (int j = 0; j<3; ++j) bTM[t][j] = new StructureObject("field1", t, 1, j, new Object3D(new BlankMask("", 1, 1, 1), j+1), mcT[t], xp);
            //dao.store(bTM[i]);
        }
        for (int i= 1; i<mcT.length; ++i) bTM[i][0].setPreviousInTrack(bTM[i-1][0], false, false);
        bTM[1][1].setPreviousInTrack(bTM[0][0], true, false);
        for (int i= 2; i<mcT.length; ++i) bTM[i][1].setPreviousInTrack(bTM[i-1][1], false, false);
        bTM[3][2].setPreviousInTrack(bTM[2][1], true, false); bTM[4][2].setPreviousInTrack(bTM[3][2], false, false);
        bTM[1][2].setPreviousInTrack(bTM[0][1], false, false); bTM[2][2].setPreviousInTrack(bTM[1][2], false, false);
        /*
        0.0->4
        -1->4
        --3->4
        1.0->2
        2.0
        */
        ArrayList<StructureObject> toStore = new ArrayList<StructureObject>();
        for (int i = 0; i<bTM.length; ++i) toStore.addAll(Arrays.asList(bTM[i]));
        dao.store(toStore, true);
        m.clearCachefor(StructureObject.class);

        // retrive tracks head for microChannels
        ArrayList<StructureObject> mcHeads = dao.getTrackHeads(rootT[0], 0);
        assertEquals("number of heads for microChannels", 1, mcHeads.size());
        assertEquals("head for microChannel", mcT[0].getId(), mcHeads.get(0).getId());
        assertEquals("head for microChannel (unique instanciation)", dao.getObject(mcT[0].getId()), mcHeads.get(0));

        // retrieve microChannel track
        ArrayList<StructureObject> mcTrack = dao.getTrack(mcT[0]);
        assertEquals("number of elements in microChannel track", 5, mcTrack.size());
        for (int i = 0; i<mcTrack.size(); ++i) assertEquals("microChannel track element: "+i, mcT[i].getId(), mcTrack.get(i).getId());
        assertEquals("head of microChannel track (unique instanciation)", mcHeads.get(0), mcTrack.get(0));
        for (int i = 0; i<mcTrack.size(); ++i) assertEquals("microChannel track element: "+i+ " unique instanciation", dao.getObject(mcT[i].getId()), mcTrack.get(i));

        // retrive tracks head for bacteries
        ArrayList<StructureObject> bHeads = dao.getTrackHeads(mcT[0], 1);
        assertEquals("number of heads for bacteries", 5, bHeads.size());
        assertEquals("head for bacteries (0)", bTM[0][0].getId(), bHeads.get(0).getId());
        assertEquals("head for bacteries (1)", bTM[0][1].getId(), bHeads.get(1).getId());
        assertEquals("head for bacteries (2)", bTM[0][2].getId(), bHeads.get(2).getId());
        assertEquals("head for bacteries (3)", bTM[1][1].getId(), bHeads.get(3).getId());
        assertEquals("head for bacteries (4)", bTM[3][2].getId(), bHeads.get(4).getId());
        assertEquals("head for bacteries (0, unique instanciation)", dao.getObject(bTM[0][0].getId()), bHeads.get(0));

        // retrieve bacteries track
        ArrayList<StructureObject> bTrack0 = dao.getTrack(bHeads.get(0));
        assertEquals("number of elements in bacteries track (0)", 5, bTrack0.size());
        for (int i = 0; i<mcTrack.size(); ++i) assertEquals("bacteries track element: "+i, bTM[i][0].getId(), bTrack0.get(i).getId());
        assertEquals("head of microChannel track (unique instanciation)", bHeads.get(0), bTrack0.get(0));
        for (int i = 0; i<mcTrack.size(); ++i) assertEquals("bacteries track element: "+i+ " unique instanciation", dao.getObject(bTM[i][0].getId()), bTrack0.get(i));

        
    }
}
