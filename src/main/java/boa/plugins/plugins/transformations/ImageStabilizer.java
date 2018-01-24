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
package boa.plugins.plugins.transformations;

import boa.configuration.parameters.Parameter;
import boa.configuration.parameters.TimePointParameter;
import boa.data_structure.input_image.InputImages;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.wrappers.ImgLib1ImageWrapper;
import boa.image.TypeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import mpicbg.imglib.algorithm.fft.PhaseCorrelation;
import mpicbg.stitching.PairWiseStitchingImgLib;
import mpicbg.stitching.PairWiseStitchingResult;
import mpicbg.stitching.StitchingParameters;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import static boa.plugins.Plugin.logger;
import boa.plugins.Transformation;
import boa.plugins.Transformation.SelectionMode;
import boa.image.processing.ImageTransformation;

/**
 *
 * @author jollion
 */
public class ImageStabilizer {
    ArrayList<ArrayList<Double>> translationTXYZ = new ArrayList<ArrayList<Double>>();
    TimePointParameter ref = new TimePointParameter("Reference time point", 50, true);
    public void computeConfigurationData(int channelIdx, InputImages inputImages) {
        int defTp = ref.getSelectedTimePoint();
        mpicbg.imglib.image.Image imRef = ImgLib1ImageWrapper.getImage(inputImages.getImage(channelIdx, defTp));
        translationTXYZ = new ArrayList<ArrayList<Double>>(inputImages.getFrameNumber());
        for (int t = 0; t<inputImages.getFrameNumber(); ++t) {
            if (t!=defTp) {
                mpicbg.imglib.image.Image im = ImgLib1ImageWrapper.getImage(inputImages.getImage(channelIdx, t));
                PairWiseStitchingResult r = PairWiseStitchingImgLib.computePhaseCorrelation(imRef, im, 5, true);
                ArrayList<Double> off = new ArrayList<Double>(3);
                for (float f : r.getOffset()) off.add((double)f);
                if (off.size()==2) off.add(0d);
                translationTXYZ.add(off);
            } else translationTXYZ.add(new ArrayList<Double>(Arrays.asList(new Double[]{0d, 0d, 0d}))); //reference time point
        }
        /*final Img< FloatType > image;
        final Img< FloatType > template;
        final FourierTransform< FloatType, ComplexFloatType > fft =
            new FourierTransform< FloatType, ComplexFloatType >(
                template, new ComplexFloatType() );*/
    }
    
    public ImageStabilizer setReferenceTimePoint(int timePoint) {
        this.ref.setTimePoint(timePoint);
        return this;
    }

    public Image applyTransformation(int channelIdx, int timePoint, Image image) {
        ArrayList<Double> trans = translationTXYZ.get(timePoint);
        logger.debug("stabilization time: {}, channel: {}, X:{}, Y:{} Z: {}", timePoint, channelIdx, trans.get(0), trans.get(1), trans.get(2));
        if (trans.get(0)==0 && trans.get(1)==0 && trans.get(2)==0) return image;
        if (!(image instanceof ImageFloat)) image = TypeConverter.toFloat(image, null);
        return ImageTransformation.translate(image, -trans.get(0), -trans.get(1), -trans.get(2), ImageTransformation.InterpolationScheme.BSPLINE5);
    }

    public ArrayList getConfigurationData() {
        return translationTXYZ;
    }

    public Parameter[] getParameters() {
        return new Parameter[] {ref};
    }

    public boolean does3D() {
        return true;
    }

    public SelectionMode getOutputChannelSelectionMode() {
        return SelectionMode.ALL;
    }
    
}
