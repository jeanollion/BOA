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

import dataStructure.objects.StructureObjectPreFilter;
import dataStructure.objects.StructureObjectProcessing;
import image.BlankMask;
import image.Image;
import image.ImageInteger;
import java.util.ArrayList;
import plugins.PostFilter;
import plugins.PreFilter;
import plugins.Segmenter;

/**
 *
 * @author jollion
 */
public class PluginSequenceRunner {
    public static Image preFilterImage(Image input, StructureObjectPreFilter structureObject, ArrayList<PreFilter> preProcessors) {
        if (preProcessors==null) return input;
        for (PreFilter p : preProcessors) {
            input = p.runPreFilter(input, structureObject);
        }
        return input;
    }
    
    public static ImageInteger segmentImage(Image input, StructureObjectProcessing structureObject, Segmenter segmenter) {
        if (segmenter==null) return new BlankMask("", input);
        else return segmenter.runSegmenter(input, structureObject);
    }
    
    public static ImageInteger postFilterImage(ImageInteger input, StructureObjectProcessing structureObject, ArrayList<PostFilter> preProcessors) {
        if (preProcessors==null) return input;
        for (PostFilter p : preProcessors) {
            input = p.runPostFilter(input, structureObject);
        }
        return input;
    }
}
