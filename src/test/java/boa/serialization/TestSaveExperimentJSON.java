/*
 * Copyright (C) 2017 jollion
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
package boa.serialization;

import boa.core.generateXP.GenerateXP;
import static boa.core.generateXP.GenerateXP.generateXPFluo;
import boa.configuration.experiment.Experiment;
import org.json.simple.JSONObject;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import boa.plugins.PluginFactory;

/**
 *
 * @author jollion
 */
public class TestSaveExperimentJSON {
    public static void main(String[] args) {
        new TestSaveExperimentJSON().testSaveXP();
    }
    @Test
    public void testSaveXP() {
        PluginFactory.findPlugins("boa.plugins.plugins");
        //Experiment xp = generateXPFluo("MotherMachineMutation", null, true, true, 0, 0, Double.NaN, null);
        Experiment xp = GenerateXP.generateXPTrans("MotherMachinePhaseContrast", null, true, 0, 0, Double.NaN);
        JSONObject ser = xp.toJSONEntry();
        Experiment xpFluoUnSer = new Experiment();
        xpFluoUnSer.initFromJSONEntry(ser);
        assertTrue("Same content", xp.sameContent(xpFluoUnSer));
    }
}
