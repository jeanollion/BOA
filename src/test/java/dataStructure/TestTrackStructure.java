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
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import static junit.framework.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author nasique
 */
public class TestTrackStructure {
    @Test
    public void testTrackStructure() {
        try {
            MorphiumConfig cfg = new MorphiumConfig();
            cfg.setDatabase("testTrack");
            cfg.addHost("localhost", 27017);
            Morphium m=new Morphium(cfg);
            m.clearCollection(Experiment.class);
            m.clearCollection(StructureObject.class);
            ExperimentDAO xpDAO = new ExperimentDAO(m);
            ObjectDAO dao = new ObjectDAO(m, xpDAO);
            
            Experiment xp = new Experiment("test");
            ChannelImage image = new ChannelImage("ChannelImage");
            xp.getChannelImages().insert(image);
            xp.getStructures().removeAllElements();
            Structure microChannel = new Structure("MicroChannel", -1, 0);
            Structure bacteries = new Structure("Bacteries", 0, 0);
            xp.getStructures().insert(microChannel, bacteries);
            bacteries.setParentStructure(0);
            xp.createMicroscopyField("field1");
            xpDAO.store(xp);
            
            StructureObject[] rootT = new StructureObject[5];
            for (int i = 0; i<rootT.length; ++i) rootT[i] = new StructureObject("field1", i, new BlankMask("", 1, 1, 1), xp);
            dao.store(rootT);
            Processor.trackRoot(xp, rootT, dao);
            
            StructureObject[] mcT = new StructureObject[5];
            for (int i = 0; i<mcT.length; ++i) mcT[i] = new StructureObject("field1", i, 0, 0, new Object3D(new BlankMask("", 1, 1, 1), 1), rootT[i], xp);
            dao.store(mcT);
            Processor.trackRoot(xp, mcT, dao);
            
            StructureObject[][] bTM = new StructureObject[5][3];
            for (int i = 0; i<bTM.length; ++i) {
                for (int j = 0; j<3; ++j) bTM[i][j] = new StructureObject("field1", i, 1, j, new Object3D(new BlankMask("", 1, 1, 1), j+1), mcT[i], xp);
                dao.store(bTM[i]);
            }
            for (int i= 1; i<mcT.length; ++i) bTM[i][0].setPreviousInTrack(bTM[i-1][0], false);
            bTM[1][1].setPreviousInTrack(bTM[0][0], true);
            for (int i= 2; i<mcT.length; ++i) bTM[i][1].setPreviousInTrack(bTM[i-1][1], false);
            bTM[3][2].setPreviousInTrack(bTM[2][1], true); bTM[4][2].setPreviousInTrack(bTM[3][2], false);
            bTM[1][2].setPreviousInTrack(bTM[0][1], false); bTM[2][2].setPreviousInTrack(bTM[1][2], false);
            /*
            0.0->4
            -1->4
            --3->4
            1.0->2
            2.0
            */
            for (int i = 0; i<bTM.length; ++i) dao.updateTrackAttributes(bTM[i]);
            
            m.clearCachefor(StructureObject.class);
            
            // retrive tracks head for microChannels
            StructureObject[] mcHeads = dao.getTrackHeads(rootT[0], 0);
            assertEquals("number of heads for microChannels", 1, mcHeads.length);
            assertEquals("head for microChannel", mcT[0].getId(), mcHeads[0].getId());
            assertEquals("head for microChannel (unique instanciation)", dao.getObject(mcT[0].getId()), mcHeads[0]);
            
            // retrieve microChannel track
            StructureObject[] mcTrack = dao.getTrack(mcT[0]);
            assertEquals("number of elements in microChannel track", 5, mcTrack.length);
            for (int i = 0; i<mcTrack.length; ++i) assertEquals("microChannel track element: "+i, mcT[i].getId(), mcTrack[i].getId());
            assertEquals("head of microChannel track (unique instanciation)", mcHeads[0], mcTrack[0]);
            for (int i = 0; i<mcTrack.length; ++i) assertEquals("microChannel track element: "+i+ " unique instanciation", dao.getObject(mcT[i].getId()), mcTrack[i]);
            
            // retrive tracks head for bacteries
            StructureObject[] bHeads = dao.getTrackHeads(mcT[0], 1);
            assertEquals("number of heads for bacteries", 5, bHeads.length);
            assertEquals("head for bacteries (0)", bTM[0][0].getId(), bHeads[0].getId());
            assertEquals("head for bacteries (1)", bTM[0][1].getId(), bHeads[1].getId());
            assertEquals("head for bacteries (2)", bTM[0][2].getId(), bHeads[2].getId());
            assertEquals("head for bacteries (3)", bTM[1][1].getId(), bHeads[3].getId());
            assertEquals("head for bacteries (4)", bTM[3][2].getId(), bHeads[4].getId());
            assertEquals("head for bacteries (0, unique instanciation)", dao.getObject(bTM[0][0].getId()), bHeads[0]);
            
            // retrieve bacteries track
            StructureObject[] bTrack0 = dao.getTrack(bHeads[0]);
            assertEquals("number of elements in bacteries track (0)", 5, bTrack0.length);
            for (int i = 0; i<mcTrack.length; ++i) assertEquals("bacteries track element: "+i, bTM[i][0].getId(), bTrack0[i].getId());
            assertEquals("head of microChannel track (unique instanciation)", bHeads[0], bTrack0[0]);
            for (int i = 0; i<mcTrack.length; ++i) assertEquals("bacteries track element: "+i+ " unique instanciation", dao.getObject(bTM[i][0].getId()), bTrack0[i]);
            
            
            
        } catch (UnknownHostException ex) {
            Logger.getLogger(TestTrackStructure.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
}