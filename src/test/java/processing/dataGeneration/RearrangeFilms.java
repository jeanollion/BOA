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
package processing.dataGeneration;

import static TestUtils.Utils.logger;
import image.Image;
import image.ImageFormat;
import image.ImageIOCoordinates;
import image.ImageReader;
import image.ImageWriter;
import java.io.File;
import java.io.FilenameFilter;

/**
 *
 * @author jollion
 */
public class RearrangeFilms {
    public static void main(String[] args) {
        //String in = "/media/jollion/TOSHIBA EXT/ME120R63-18022016-LR62r";
        String in = "/data/Images/Fluo/film160212/ME120R63-120220166LR62r";
        String out = "/data/Images/Fluo/film160212/ImagesSubset0-120";
        /*String replace1 = "2016_";
        String replace2 = "2017_";
        mergeFilms(in, out, replace1, replace2);*/
        subsetTimePoints(in, out, 0, 121);
    }
    public static void mergeFilms(String inputDir, String outputDir, final String stringFirstTimepoints, final String stringLastTimePoints) {
        File in = new File(inputDir);
        File out = new File(outputDir);
        if (!in.exists()) throw new IllegalArgumentException("input dir does not exists");
        if (!out.exists()) throw new IllegalArgumentException("output dir does not exists");
        File[] firstTimePoints = in.listFiles(new FilenameFilter() {
            public boolean accept(File arg0, String arg1) {
                return arg1.contains(stringFirstTimepoints) && !arg1.endsWith(".log");
            }
        });
        int processed=0;
        int failed = 0;
        for (File f : firstTimePoints) {
            logger.debug("Processing File: {}, exists: {}", f.getAbsolutePath(), f.exists());
            File fEnd = new File(f.getAbsolutePath().replace(stringFirstTimepoints, stringLastTimePoints));
            if (!fEnd.exists()) {
                logger.debug("File: {} not found (end of: {})", fEnd.getAbsolutePath(), f.getAbsolutePath());
                ++failed;
                continue;
            }
            ImageReader r = new ImageReader(f.getAbsolutePath());
            ImageReader r2 = new ImageReader(fEnd.getAbsolutePath());
            int nTimePoints1 = r.getSTCXYZNumbers()[0][0];
            int nTimePoints2 = r2.getSTCXYZNumbers()[0][0];
            logger.debug("file: {}, tp1: {}, tp2: {}", f.getAbsoluteFile(), nTimePoints1, nTimePoints2);
            int nTimePoints = nTimePoints1 + nTimePoints2;
            Image[][] imagesTC = new Image[nTimePoints][1];
            for (int t = 0; t<nTimePoints1; ++t) imagesTC[t][0] = r.openImage(new ImageIOCoordinates(0, 0, t));
            for (int t = 0; t<nTimePoints2; ++t) imagesTC[t+nTimePoints1][0] = r2.openImage(new ImageIOCoordinates(0, 0, t));
            ImageWriter.writeToFile(outputDir, r.getImageTitle(), ImageFormat.OMETIF, imagesTC);
            r.closeReader();
            r2.closeReader();
            ++processed;
        }
        logger.info("Total Files: {}, processed: {}, failed: {}", firstTimePoints.length, processed, failed);
    }
    // tEndExcluded
    public static void subsetTimePoints(String inputDir, String outputDir, int tStart, int tEnd) {
        
        File in = new File(inputDir);
        File out = new File(outputDir);
        if (!in.exists()) throw new IllegalArgumentException("input dir does not exists");
        if (!out.exists()) throw new IllegalArgumentException("output dir does not exists");
        File[] allFiles = in.listFiles(new FilenameFilter() {
            public boolean accept(File arg0, String arg1) {
                return !arg1.endsWith(".log");
            }
        });
        logger.info("Total Files: {}", allFiles.length);
        for (File f : allFiles) {
            logger.debug("Processing File: {}, exists: {}", f.getAbsolutePath(), f.exists());
            ImageReader r = new ImageReader(f.getAbsolutePath());
            int nTimePointIn = r.getSTCXYZNumbers()[0][0];
            int tEndTemp = tEnd;
            if (tEnd>nTimePointIn) tEndTemp = nTimePointIn;
            logger.debug("file: {}, tp count: {}", f.getAbsoluteFile(), nTimePointIn);
            int nTimePointsOut = tEndTemp - tStart;
            Image[][] imagesTC = new Image[nTimePointsOut][1];
            int count = 0;
            for (int t = tStart; t<tEndTemp; ++t) imagesTC[count++][0] = r.openImage(new ImageIOCoordinates(0, 0, t));
            ImageWriter.writeToFile(outputDir, r.getImageTitle(), ImageFormat.OMETIF, imagesTC);
            r.closeReader();
        }
        
    }
}
