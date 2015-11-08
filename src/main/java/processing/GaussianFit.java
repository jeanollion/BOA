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

import static core.Processor.logger;
import dataStructure.objects.Voxel;
import image.Image;
import image.ImgLib2ImageWrapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.localization.EllipticGaussianOrtho;
import net.imglib2.algorithm.localization.FunctionFitter;
import net.imglib2.algorithm.localization.LevenbergMarquardtSolver;
import net.imglib2.algorithm.localization.LocalizationUtils;
import net.imglib2.algorithm.localization.MLEllipticGaussianEstimator;
import net.imglib2.algorithm.localization.Observation;
import net.imglib2.algorithm.localization.PeakFitter;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedByteType;

/**
 *
 * @author jollion
 */
public class GaussianFit {
    public static Map<Voxel, double[]> run(Image image, List<Voxel> peaks, double typicalSigma) {
        return run(image, peaks, typicalSigma, 300, 1e-3d, 1e-1d);
    }
    
    public static Map<Voxel, double[]> run(Image image, List<Voxel> peaks, double typicalSigma, int maxIter, double lambda, double termEpsilon ) {
        double[] sigmas = new double[image.getSizeZ()>1 ? 3 :2];
        for (int i = 0; i<sigmas.length; ++i) sigmas[i]=typicalSigma;
        return run(image, peaks, sigmas, maxIter, lambda, termEpsilon);
    }
    /**
     * 
     * @param image
     * @param peaks
     * @param typicalSigmas
     * @param maxIter
     * @param lambda
     * @param termEpsilon
     * @return for each peak array of fitted parameters: coordinates, intensity@peak, 1/sigma2 in each dimension, error
     */
    public static Map<Voxel, double[]> run(Image image, List<Voxel> peaks, double[] typicalSigmas, int maxIter, double lambda, double termEpsilon ) {
        boolean is3D = image.getSizeZ()>1;
        Img img = ImgLib2ImageWrapper.getImage(image);
        MLEllipticGaussianEstimator estimator = new MLEllipticGaussianEstimator(typicalSigmas);
        EllipticGaussianOrtho fitFunction = new EllipticGaussianOrtho();
        PeakFitter<UnsignedByteType> fitter = new PeakFitter<UnsignedByteType>(img, getPeaksLocalizables(peaks, is3D), 
				new LevenbergMarquardtSolver(maxIter, lambda, termEpsilon), fitFunction, estimator);
        if ( !fitter.checkInput() || !fitter.process()) {
            logger.error("Problem with peak fitting: {}", fitter.getErrorMessage());
            return null;
        }
        logger.debug("Peak fitting of {} peaks, using {} threads, done in {} ms.", peaks.size(), fitter.getNumThreads(), fitter.getProcessingTime());
        
        Map<Localizable, double[]> results = fitter.getResult();
        Map<Voxel, double[]> results2 = new HashMap<Voxel, double[]>(results.size());
        for (Entry<Localizable, double[]> e : results.entrySet()) {
            Observation data = LocalizationUtils.gatherObservationData(img, e.getKey(), estimator.getDomainSpan());
            double[] params = new double[e.getValue().length+1];
            System.arraycopy(e.getValue(), 0, params, 0, e.getValue().length);
            params[params.length-1] = LevenbergMarquardtSolver.chiSquared(data.X, e.getValue(), data.I, fitFunction); // error
            results2.put(convertLocalizable(e.getKey(), is3D), params);
        }
        return results2;
    }
    private static Localizable convertVoxel(Voxel v, boolean is3D) {
        if (is3D) return new Point((long)v.x, (long)v.y, (long)v.z);
        else return new Point((long)v.x, (long)v.y);
    }
    private static Voxel convertLocalizable(Localizable l, boolean is3D) {
        if (is3D) return new Voxel(l.getIntPosition(0), l.getIntPosition(1), l.getIntPosition(2));
        else return new Voxel(l.getIntPosition(0), l.getIntPosition(1), 0);
    }
    private static List<Localizable> getPeaksLocalizables(List<Voxel> peaks, boolean is3D) {
        ArrayList<Localizable> res = new ArrayList<Localizable>(peaks.size());
        for (Voxel v : peaks) res.add(convertVoxel(v, is3D));
        return res;
    }
}
