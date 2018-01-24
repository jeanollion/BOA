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
package boa.image.processing;

import boa.data_structure.Region;
import boa.data_structure.Voxel;
import boa.image.Image;
import static boa.image.Image.logger;
import boa.image.wrappers.ImgLib2ImageWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static jdk.nashorn.internal.objects.NativeRegExp.source;
import net.imglib2.Point;
import net.imglib2.algorithm.localextrema.RefinedPeak;
import net.imglib2.algorithm.localextrema.SubpixelLocalization;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.utils.HashMapGetCreate;
import boa.utils.Pair;
import boa.utils.Utils;

/**
 *
 * @author jollion
 */
public class SubPixelLocalizator {
    public static boolean debug;
    public final static Logger logger = LoggerFactory.getLogger(SubPixelLocalizator.class);
    public static List<Point> getPeaks(Image img, List<Region> objects) {
        List<Point> peaks = new ArrayList<>(objects.size());
        for (Region o : objects) { // get max value within map
            double max = Double.NEGATIVE_INFINITY;
            Voxel maxV= null;
            for (Voxel v : o.getVoxels()) {
                double value = img.getPixel(v.x, v.y, v.z);
                if (value>max) {
                    max = value;
                    maxV = v;
                }
            }
            if (img.getSizeZ()>1) peaks.add(new Point(maxV.x, maxV.y, maxV.z));
            else peaks.add(new Point(maxV.x, maxV.y));
        }
        return peaks;
    }
    public static ArrayList< RefinedPeak< Point >> getSubLocPeaks(Image img, List<Point> peaks) {
        Img source = ImgLib2ImageWrapper.getImage(img);
        final SubpixelLocalization< Point, ? extends RealType > spl = new SubpixelLocalization<>( source.numDimensions() );
        //logger.debug("source sizeZ: {}, numDim: {}", img.getSizeZ(), source.numDimensions());
        spl.setNumThreads( 1 );
        spl.setReturnInvalidPeaks( true );
        spl.setCanMoveOutside( true );
        spl.setAllowMaximaTolerance( true );
        spl.setMaxNumMoves( 10 );
        return spl.process( peaks, source, source );
    }
    
    public static void setSubPixelCenter(Image img, List<Region> objects, boolean setQuality) {
        if (objects.isEmpty()) return;
        List<Point> peaks = getPeaks(img, objects);
        List<RefinedPeak< Point >> refined = getSubLocPeaks(img, peaks);
        if (debug) {
            logger.debug("num peaks: {}, refined: {}", peaks.size(), refined.size());
            logger.debug("peaks: {}", Utils.toStringList(peaks, p->p.toString()));
            logger.debug("refined: {}", Utils.toStringList(refined, p->p.getValue()==0? "NaN" : p.toString()));
            //logger.debug("refined: {}", Utils.toStringList(refined, p->"["+p.getDoublePosition(0)+"; "+p.getDoublePosition(1)+(img.getSizeZ()>1? ";"+p.getDoublePosition(2): "")+"]"));
        }
        for (RefinedPeak< Point > r : refined) {
            Region o = objects.get(peaks.indexOf(r.getOriginalPeak()));
            double[] position= new double[img.getSizeZ()>1?3 : 2];
            position[0] = r.getDoublePosition(0);
            position[1] = r.getDoublePosition(1);
            if (img.getSizeZ()>1) position[2] = r.getDoublePosition(2);
            o.setCenter(position);
            if (setQuality) o.setQuality(r.getValue());
        }
    }
   
}
