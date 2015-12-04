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
package processing;

import core.Processor;

/**
 *
 * @author jollion
 */
public class GenerateTestXP {
    public static void main(String[] args) {
        String dbName = "fluo151130";
        TestProcessBacteria t = new TestProcessBacteria();
        t.setUpXp(true, "/data/Images/Fluo/films1511/151130/Output");
        //t.setUpXp(true, "/data/Images/Fluo/films1510/Output");
        //t.setUpXp(true, "/home/jollion/Documents/LJP/DataLJP/TestOutput60");
        //t.testImport("/data/Images/Fluo/testsub595-630");
        t.testImport("/data/Images/Fluo/films1511/151130/champ1");
        //t.testImport("/data/Images/Fluo/films1510/63me120r-14102015-LR62R1-lbiptg100x_1");
        //t.testImport("/home/jollion/Documents/LJP/DataLJP/test");
        t.saveXP(dbName);
        t.process(dbName, true);
    }
}
