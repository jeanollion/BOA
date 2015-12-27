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
import dataStructure.containers.MultipleImageContainerChannelSerie;
import dataStructure.containers.MultipleImageContainerSingleFile;
import image.ImageReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import utils.Utils;

/**
 *
 * @author jollion
 */

public class ImageFieldFactory {
    private final static String seriesSeparator = "_xy";
    private final static List<String> ignoredExtensions = Arrays.asList(new String[]{".log"});
    public static ArrayList<MultipleImageContainer> importImages(String[] path, Experiment xp) {
        ArrayList<MultipleImageContainer> res = new ArrayList<MultipleImageContainer>();
        if (xp.getImportImageMethod().equals(Experiment.ImportImageMethod.SINGLE_FILE)) for (String p : path) ImageFieldFactory.importImagesSingleFile(new File(p), xp, res);
        else if (xp.getImportImageMethod().equals(Experiment.ImportImageMethod.ONE_FILE_PER_CHANNEL_AND_FIELD)) {
            // get keywords
            int nb = xp.getChannelImages().getChildCount();
            String[] keyWords = new String[nb];
            int idx = 0;
            for (ChannelImage i : xp.getChannelImages().getChildren()) keyWords[idx++] = i.getImportImageChannelKeyword();
            logger.debug("import image channel: keywords: {}", (Object)keyWords);
            for (String p : path) ImageFieldFactory.importImagesChannel(new File(p), xp, keyWords, res);
        }
        Collections.sort(res, new Comparator<MultipleImageContainer>() {
            @Override public int compare(MultipleImageContainer arg0, MultipleImageContainer arg1) {
                return arg0.getName().compareToIgnoreCase(arg1.getName());
            }
        });
        return res;
    }
    
    
    protected static void importImagesSingleFile(File f, Experiment xp, ArrayList<MultipleImageContainer> containersTC) {
        if (f.isDirectory()) {
            for (File ff : f.listFiles()) {
                ImageFieldFactory.importImagesSingleFile(ff, xp, containersTC);
            }
        } else if (!isIgnoredExtension(f.getName())) {
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
                if (tc[1]==xp.getChannelImageCount()) {
                    containersTC.add(new MultipleImageContainerSingleFile(removeExtension(image.getName())+end, image.getAbsolutePath(),s, tc[0], tc[1], tc[4]));
                    logger.info("image {} imported successfully", image.getAbsolutePath());
                } else {
                    logger.warn("Invalid Image: {} has: {} channels instead of: {}", image.getAbsolutePath(), tc[1], xp.getChannelImageCount());
                }
                ++s;
            }
        }
    }
    
    protected static void importImagesChannel(File input, Experiment xp, String[] keyWords, ArrayList<MultipleImageContainer> containersTC) {
        if (!input.isDirectory()) return;
        File[] subDirs = input.listFiles(getDirectoryFilter()); // recursivity
        for (File dir : subDirs) importImagesChannel(dir, xp, keyWords, containersTC);// recursivity
        
        File[] file0 = input.listFiles(getFileFilterKeyword(keyWords[0]));
        logger.debug("import images in dir: {} number of candidates: {}", input.getAbsolutePath(), file0.length);
        for (File f : file0) {
            String[] allChannels = new String[keyWords.length];
            allChannels[0] = f.getAbsolutePath();
            boolean allFiles = true;
            for (int c = 1; c < keyWords.length; ++c) {
                String name = input + File.separator + f.getName().replace(keyWords[0], keyWords[c]);
                File channel = new File(name);
                if (!channel.exists()) {
                    logger.warn("missing file: {}", name);
                    allFiles=false;
                    break;
                } else allChannels[c] = name;
            }
            if (allFiles) {
                String name = removeExtension(f.getName().replace(keyWords[0], ""));
                addContainerChannel(allChannels, name, xp, containersTC);
            }
            
        }
    }
    
    protected static void addContainerChannel(String[] imageC, String fieldName, Experiment xp, ArrayList<MultipleImageContainer> containersTC) {
        //checks timepoint number is equal for all channels
        int timePointNumber=0;
        int[] sizeZC = new int[imageC.length];
        for (int c = 0; c< imageC.length; ++c) {
            ImageReader reader = null;
            try {
                reader = new ImageReader(imageC[c]);
            } catch (Exception e) {
                logger.warn("Image {} could not be read: ", imageC[c], e);
            }
            if (reader != null) {
                int[][] stc = reader.getSTCXYZNumbers();
                if (stc.length>1) logger.warn("Import method selected = one file per channel and per microscopy field, but file: {} contains {} series", imageC[c], stc.length);
                if (stc[0][1]>1) logger.warn("Import method selected = one file per channel and per microscopy field, but file: {} contains {} channels", imageC[c], stc[0][1]);
                if (c==0) timePointNumber=stc[0][0];
                else if (stc[0][0]!=timePointNumber) {
                    logger.warn("Warning: invalid file: {}. Contains {} time points whereas file: {} contains: {} time points", imageC[c], stc[0][0], imageC[0], timePointNumber);
                    return;
                }
                sizeZC[c] = stc[0][4];
            }
        }
        if (timePointNumber>0) {
            MultipleImageContainerChannelSerie c = new MultipleImageContainerChannelSerie(fieldName, imageC, timePointNumber, sizeZC);
            containersTC.add(c);
        }
        
        
    }
    
    
    private static String removeExtension(String s) {
        if (s.indexOf(".")>0) s= s.substring(0, s.lastIndexOf(".")); // removes the extension
        s=s.replaceAll(".", "_"); // removes all "."
        return s;
    }
    
    private static boolean isIgnoredExtension(String s) {
        for (int i =s.length()-1; i>=0; --i) {
            if (s.charAt(i)=='.') {
                String ext = s.substring(i, s.length());
                return ignoredExtensions.contains(ext);
            }
        }
        return false;
    }
    
    private static FileFilter getDirectoryFilter() {
        return new FileFilter() {
            public boolean accept(File file) {
                return file.isDirectory();
            }
        };
    }
    
    private static FileFilter getFileFilter() {
        return new FileFilter() {
            public boolean accept(File file) {
                return !file.isDirectory();
            }
        };
    }
    
    private static FilenameFilter getFileFilterKeyword(final String keyword) {
        return new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return name.contains(keyword) && !isIgnoredExtension(name);
            }
        };
    }
    
    
}
