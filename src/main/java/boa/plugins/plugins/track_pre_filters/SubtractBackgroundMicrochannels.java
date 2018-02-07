/*
 * Copyright (C) 2018 jollion
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
package boa.plugins.plugins.track_pre_filters;

import boa.configuration.parameters.BooleanParameter;
import boa.configuration.parameters.BoundedNumberParameter;
import boa.configuration.parameters.NumberParameter;
import boa.configuration.parameters.Parameter;
import boa.data_structure.StructureObject;
import boa.gui.imageInteraction.ImageWindowManagerFactory;
import boa.gui.imageInteraction.TrackMaskY;
import boa.image.BoundingBox;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.TypeConverter;
import boa.image.processing.ImageOperations;
import static boa.image.processing.ImageOperations.pasteImage;
import boa.image.processing.ImageTransformation;
import boa.plugins.TrackPreFilter;
import boa.plugins.plugins.pre_filters.IJSubtractBackground;
import java.util.ArrayList;
import java.util.TreeMap;

/**
 *
 * @author jollion
 */
public class SubtractBackgroundMicrochannels implements TrackPreFilter{
    BooleanParameter isDarkBck = new BooleanParameter("Image Background", "Dark", "Light", false);
    BooleanParameter smooth = new BooleanParameter("Perform Smoothing", false);
    NumberParameter radius = new BoundedNumberParameter("Radius", 3, 1, 0.01, null).setToolTipText("Higher value -> less homogeneity/more precision. Radius of the paraboloïd will be the sum of all michrochannel length divided by this value.");
    Parameter[] parameters = new Parameter[]{radius, isDarkBck, smooth};
    @Override
    public void filter(int structureIdx, TreeMap<StructureObject, Image> preFilteredImages, boolean canModifyImages) throws Exception {
        //smooth.setSelected(true);
        // construct one single image 
        TrackMaskY tm = new TrackMaskY(new ArrayList<>(preFilteredImages.keySet()), structureIdx, true);
        ImageFloat allImagesY = (ImageFloat)tm.generateEmptyImage("sub mc", new ImageFloat("", 0, 0, 0));
        ImageFloat floatImage=null;
        for (StructureObject o : tm.getParents()) {
            Image im = preFilteredImages.get(o);
            if (!(im instanceof ImageFloat)) {
                floatImage = TypeConverter.toFloat(im, floatImage);
                im = floatImage;
            }
            pasteImage(im, allImagesY, tm.getObjectOffset(o));
        }
        int sizeY = allImagesY.getSizeY();
        allImagesY = mirrorY(allImagesY); // mirror image on both Y ends
        // apply filter
        double radius = allImagesY.getSizeY()/this.radius.getValue().doubleValue();
        IJSubtractBackground.filter(allImagesY, radius, true, !isDarkBck.getSelected(), smooth.getSelected(), false, false);
        allImagesY = allImagesY.crop(new BoundingBox(0, allImagesY.getSizeX()-1, sizeY, 2*sizeY-1, 0, allImagesY.getSizeZ()-1)); // crop
        // recover data
        if (canModifyImages && floatImage==null) { // paste inside original images
            for (StructureObject o : tm.getParents()) ImageOperations.pasteImage(allImagesY, preFilteredImages.get(o), null, tm.getObjectOffset(o));
        } else { // crop and replace original images
            for (StructureObject o : tm.getParents()) preFilteredImages.replace(o, allImagesY.crop(tm.getObjectOffset(o)));
        }
    }
    
    private static ImageFloat mirrorY(ImageFloat input) { 
        ImageFloat res = new ImageFloat("", input.getSizeX(), 3*input.getSizeY(), input.getSizeZ());
        ImageOperations.pasteImage(input, res, new BoundingBox(0, input.getSizeY(), 0));
        Image imageFlip = ImageTransformation.flip(input, ImageTransformation.Axis.Y);
        ImageOperations.pasteImage(imageFlip, res, null);
        ImageOperations.pasteImage(imageFlip, res, new BoundingBox(0, 2*input.getSizeY(), 0));
        return res;
    }

    @Override
    public Parameter[] getParameters() {
        return parameters;
    }
    
}
