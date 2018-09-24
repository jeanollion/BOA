/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.plugins;

import boa.data_structure.StructureObject;
import boa.data_structure.StructureObjectTrackCorrection;
import java.util.ArrayList;

/**
 *
 * @author Jean Ollion
 */
public interface TrackCorrector extends Plugin {
    /**
     * 
     * @param track
     * @param splitter
     * @param correctedObjects a list of following modified objects merged (but not the merged to be erased), splitted, or with modification on track attributes. It can be null.
     */
    public void correctTrack(StructureObjectTrackCorrection track, ObjectSplitter splitter, ArrayList<StructureObjectTrackCorrection> correctedObjects);
}
