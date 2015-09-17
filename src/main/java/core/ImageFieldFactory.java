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
package core;

import static core.Processor.logger;
import dataStructure.configuration.ChannelImage;
import dataStructure.configuration.Experiment;
import dataStructure.containers.MultipleImageContainer;
import dataStructure.containers.MultipleImageContainerSingleFile;
import image.ImageReader;
import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import utils.Utils;

/**
 *
 * @author jollion
 */

public class ImageFieldFactory {
    private final static String seriesSeparator = "_xy";
    public static ArrayList<MultipleImageContainer> importImages(String[] path, Experiment xp) {
        ArrayList<MultipleImageContainer> res = new ArrayList<MultipleImageContainer>();
        if (xp.getImportImageMethod().equals(Experiment.ImportImageMethod.SINGLE_FILE)) for (String p : path) ImageFieldFactory.importImagesSingleFile(new File(p), xp, res);
        else if (xp.getImportImageMethod().equals(Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL)) {
            // get keywords
            int nb = xp.getChannelImages().getChildCount();
            String[] keyWords = new String[nb];
            int idx = 0;
            for (ChannelImage i : xp.getChannelImages().getChildren()) keyWords[idx++] = i.getImportImageChannelKeyword();
            for (String p : path) ImageFieldFactory.importImagesChannel(new File(p), xp, keyWords, res);
        }
        return res;
    }
    
    
    protected static void importImagesSingleFile(File f, Experiment xp, ArrayList<MultipleImageContainer> containersTC) {
        if (f.isDirectory()) {
            for (File ff : f.listFiles()) {
                ImageFieldFactory.importImagesSingleFile(ff, xp, containersTC);
            }
        } else {
            addContainerSingleFile(f, xp, containersTC);
        }
    }
    
    protected static void addContainerSingleFile(File image, Experiment xp, ArrayList<MultipleImageContainer> containersTC) {
        ImageReader reader=null;
        try {
            reader = new ImageReader(image.getAbsolutePath());
        } catch(Exception e) {
            Logger.getLogger(ImageFieldFactory.class.getName()).log(Level.WARNING, "Image could not be read: {0}", image.getAbsolutePath());
        }
        if (reader!=null) {
            int[][] stc = reader.getSTCXYZNumbers();
            int s = 0;
            String end = "";
            int digits=(int)(Math.log10(stc.length)+0.5);
            for (int[] tc:stc) {
                if (stc.length>1) end = seriesSeparator+Utils.formatInteger(digits, s);
                if (tc[1]==xp.getChannelImageNB()) {
                    containersTC.add(new MultipleImageContainerSingleFile(removeExtension(image.getName())+end, image.getAbsolutePath(),s, tc[0], tc[1]));
                    modify logger
                    Logger.getLogger(ImageFieldFactory.class.getName()).log(Level.INFO, "Imported Image: {0}", image.getAbsolutePath());
                } else {
                    modify logger
                    Logger.getLogger(ImageFieldFactory.class.getName()).log(Level.WARNING, "Invalid Image: {0} has: {1} channels instead of: {2}", new Object[]{image.getAbsolutePath(), tc[1], xp.getChannelImageNB()});
                }
                ++s;
            }
        }
    }
    
    protected static void importImagesChannel(File f, Experiment xp, String[] keyWords, ArrayList<MultipleImageContainer> containersTC) {
        // test xp.getChannelImageNB()
    }
    
    protected static void addContainerChannel(File[] imageC, String fieldName, Experiment xp, ArrayList<MultipleImageContainer> containersTC) {
        //checks timepoint number for all channels
        int timePointNumber;
        for (int c = 0; c< imageC.length; ++c) {
            ImageReader reader = null;
            try {
                reader = new ImageReader(imageC[0].getAbsolutePath());
            } catch (Exception e) {
                Logger.getLogger(ImageFieldFactory.class.getName()).log(Level.WARNING, "Image could not be read: {0}", imageC[0].getAbsolutePath());
            }
            if (reader != null) {
                int[][] stc = reader.getSTCXYZNumbers();
                if (stc.length>1) logger.warn("Import method selected = one file per channel and per microscopy field, but file: {} contains multiple series", imageC[c]);
                for (int[] tc : stc) { only for the first one
                    if (stc.length > 1) {
                        end = seriesSeparator + Utils.formatInteger(digits, s);
                    }
                    if (tc[1] == xp.getChannelImageNB()) {
                        containersTC.add(new MultipleImageContainerSingleFile(removeExtension(image.getName()) + end, image.getAbsolutePath(), s, tc[0], tc[1]));
                        Logger.getLogger(ImageFieldFactory.class.getName()).log(Level.INFO, "Imported Image: {0}", image.getAbsolutePath());
                    } else {
                        Logger.getLogger(ImageFieldFactory.class.getName()).log(Level.WARNING, "Invalid Image: {0} has: {1} channels instead of: {2}", new Object[]{image.getAbsolutePath(), tc[1], xp.getChannelImageNB()});
                    }
                    ++s;
                }
            }
        }
        
        MultipleImageContainerChannel c = new MultipleImageContainerChannel(fieldName, imageC, );
        
    }
    
    
    private static String removeExtension(String s) {
        if (s.indexOf(".")>0) return s.substring(0, s.indexOf("."));
        else return s;
    }
}
