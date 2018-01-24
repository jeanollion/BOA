package boa.gaussian_fit;

import ij.ImagePlus;
import ij.gui.EllipseRoi;
import ij.gui.Overlay;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;

import net.imglib2.Localizable;
import net.imglib2.Point;
import net.imglib2.algorithm.localization.Gaussian;
import net.imglib2.algorithm.localization.LevenbergMarquardtSolver;
import net.imglib2.algorithm.localization.LocalizationUtils;
import net.imglib2.algorithm.localization.MLGaussianEstimator;
import net.imglib2.algorithm.localization.PeakFitter;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.ByteArray;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.integer.UnsignedByteType;

public class GaussianFitTestDrive0 {

	public static void main(String[] args) {

		int width = 100;
		int height = 100;
		double sigma_noise = 5;
		final int nspots = 10;

		System.out.println("Preparing image");
		long[] dimensions = new long[] { width, height };
		ArrayImg<UnsignedByteType,ByteArray> img = ArrayImgs.unsignedBytes(dimensions);
		
		Random rangen = new Random();
		Collection<Localizable> peaks = new HashSet<Localizable>(nspots);
		Map<Localizable, double[]> groundTruth = new HashMap<Localizable, double[]>(nspots);
		
		for (int i = 0; i < nspots; i++) {
			
			for (int j = 0; j < nspots; j++) {
				

				double A = 100 + 10 * rangen.nextGaussian();
				double x0 =  width / (double) nspots * i * 1.02d; 
				double y0 =  width / (double) nspots * j * 1.02d;
				double sigma = 2 + 0.5 * rangen.nextGaussian();

				Localizable peak = new Point((long) x0, (long) y0);
				peaks.add(peak);

				double[] params = new double[] {x0, y0, A, 1/sigma/sigma };
				LocalizationUtils.addGaussianSpotToImage(img, params);
				groundTruth.put(peak, params);
				
			}
		}
		LocalizationUtils.addGaussianNoiseToImage(img, sigma_noise);

		// Show target image
		ij.ImageJ.main(args);
		final ImagePlus imp = ImageJFunctions.wrap(img, "Target");
		imp.show();
		imp.resetDisplayRange();
		imp.updateAndDraw();

		final Overlay overlay = new Overlay();
		imp.setOverlay(overlay);

		// Instantiate fitter once
//		PeakFitter<UnsignedByteType> fitter = new PeakFitter<UnsignedByteType>(img, peaks, 
//				new DummySolver(), new Gaussian(), new MLGaussianEstimator(2d, 2));
		PeakFitter<UnsignedByteType> fitter = new PeakFitter<UnsignedByteType>(img, peaks, 
				new LevenbergMarquardtSolver(), new Gaussian(), new MLGaussianEstimator(2d, 2));
		
		System.out.println(fitter);
		if ( !fitter.checkInput() || !fitter.process()) {
			System.err.println("Problem with peak fitting: " + fitter.getErrorMessage());
			return;
		}
		System.out.println("Peak fitting of " + (nspots*nspots) + " peaks, using " +
				fitter.getNumThreads() + " threads, done in " + fitter.getProcessingTime() + " ms.");
		
		Map<Localizable, double[]> results = fitter.getResult();
		
		for (Localizable peak : peaks) {
			double[] params = results.get(peak);

			double Ar = params[2];
			double x = params[0];
			double y = params[1];
			double s = 1/Math.sqrt(params[3]);

			System.out.println(String.format("- For " + peak + "\n - Found      : " +
					"A = %6.2f, x0 = %6.2f, y0 = %6.2f, s = %5.2f", 
					Ar, x, y, s));
			double[] truth = groundTruth.get(peak);
			System.out.println(String.format(" - Real values: " +
					"A = %6.2f, x0 = %6.2f, y0 = %6.2f, s = %5.2f",
					truth[2], truth[0], truth[1], 1 / Math.sqrt(truth[3])) );

			// Draw ellipse on the target image
			double x1, x2, y1, y2, ar;
			x1 = x - 2.3548 * s / 2 + 0.5;
			x2 = x + 2.3548 * s / 2 + 0.5;
			y1 = y + 0.5;
			y2 = y + 0.5;
			ar = 1; 
			overlay.add(new EllipseRoi(x1, y1, x2, y2, ar));
			imp.updateAndDraw();

		}

	}
}