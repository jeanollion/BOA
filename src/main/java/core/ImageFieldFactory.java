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

import dataStructure.configuration.Experiment;
import dataStructure.containers.MultipleImageContainerSingleFile;
import image.ImageReader;
import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author jollion
 */

public class ImageFieldFactory {
    private final static DecimalFormat nf3 = new DecimalFormat("000");
    public static ArrayList<MultipleImageContainerSingleFile> importImages(String[] path, Experiment xp) {
        ArrayList<MultipleImageContainerSingleFile> res = new ArrayList<MultipleImageContainerSingleFile>();
        for (String p : path) ImageFieldFactory.importImages(new File(p), xp, res);
        return res;
    }
    
    protected static void importImages(File f, Experiment xp, ArrayList<MultipleImageContainerSingleFile> containersTC) {
        if (f.isDirectory()) {
            for (File ff : f.listFiles()) ImageFieldFactory.importImages(ff, xp, containersTC);
        } else {
            if (xp.getImportImageMethod().equals(Experiment.ImportImageMethod.BIOFORMATS)) {
                importFieldBioFormats(f, xp, containersTC);
            }
        }
    }
    
    protected static void importFieldBioFormats(File image, Experiment xp, ArrayList<MultipleImageContainerSingleFile> containersTC) {
        ImageReader reader=null;
        try {
            reader = new ImageReader(image.getAbsolutePath());
        } catch(Exception e) {
            Logger.getLogger(ImageFieldFactory.class.getName()).log(Level.WARNING, "Image could not be read: {0}", image.getAbsolutePath());
        }
        if (reader!=null) {
            int[][] stc = reader.getSTCNumbers();
            int s = 0;
            String end = "";
            for (int[] tc:stc) {
                if (stc.length>1) end = "_s"+nf3.format(s);
                if (tc[1]==xp.getChannelImageNB()) {
                    containersTC.add(new MultipleImageContainerSingleFile(removeExtension(image.getName())+end, image.getAbsolutePath(),s, tc[0], tc[1]));
                    Logger.getLogger(ImageFieldFactory.class.getName()).log(Level.INFO, "Imported Image: {0}", image.getAbsolutePath());
                } else {
                    Logger.getLogger(ImageFieldFactory.class.getName()).log(Level.WARNING, "Invalid Image: {0} has: {1} channels instead of: {2}", new Object[]{image.getAbsolutePath(), tc[1], xp.getChannelImageNB()});
                }
                ++s;
            }
        }
    }
    
    private static String removeExtension(String s) {
        if (s.indexOf(".")>0) return s.substring(0, s.indexOf("."));
        else return s;
    }
}
