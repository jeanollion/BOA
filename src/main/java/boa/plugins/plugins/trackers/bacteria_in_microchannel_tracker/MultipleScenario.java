/*
 * Copyright (C) 2016 jollion
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
package boa.plugins.plugins.trackers.bacteria_in_microchannel_tracker;

import java.util.List;

/**
 *
 * @author jollion
 */
public class MultipleScenario extends CorrectionScenario {
        final List<CorrectionScenario> scenarios;

        public MultipleScenario(BacteriaClosedMicrochannelTrackerLocalCorrections tracker, List<CorrectionScenario> sortedScenarios) {
            super(sortedScenarios.isEmpty()? 0 :sortedScenarios.get(0).frameMin, sortedScenarios.isEmpty()? 0 : sortedScenarios.get(sortedScenarios.size()-1).frameMax, tracker);
            this.scenarios = sortedScenarios;
            if (scenarios.isEmpty()) this.cost = Double.POSITIVE_INFINITY;
            else for (CorrectionScenario s : scenarios) this.cost+=s.cost;
        }
        
        @Override
        protected CorrectionScenario getNextScenario() {
            return null;
        }

        @Override
        protected void applyScenario() {
            for (CorrectionScenario s : scenarios) s.applyScenario();
        }
        @Override 
        public String toString() {
            return "MultipleScenario ["+frameMin+";"+frameMax+"]" + (this.scenarios.isEmpty() ? "": " first: "+scenarios.get(0).toString());
        }
    }
