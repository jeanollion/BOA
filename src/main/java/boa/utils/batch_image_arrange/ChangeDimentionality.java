/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BOA
 *
 * BOA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BOA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BOA.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.utils.batch_image_arrange;

import boa.image.Image;
import boa.image.io.ImageIOCoordinates;
import boa.image.io.ImageReader;
import boa.image.io.ImageWriter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import boa.utils.HashMapGetCreate;

/**
 *
 * @author jollion
 */
public class ChangeDimentionality {
    public static final Logger logger = LoggerFactory.getLogger(ChangeDimentionality.class);
    public static void main(String[] args) {
        String input = "/data/Images/Fluo/fluo171219_WT_750ms/input/testArrange3D/171219_WT05_03_R3D.dv";
        String dFolder = "/data/Images/Fluo/fluo171219_WT_750ms/input/testArrange3D/converted";
        fromTToZT(input, dFolder, 3);
    }
    private static void fromTToZT(String input, String destFolder, int sizeZ) {
        ImageReader reader = new ImageReader(input);
        int[] tcxyz = reader.getSTCXYZNumbers()[0];
        logger.debug("image dims fcxyz: {}", tcxyz);
        if (tcxyz[0]%sizeZ!=0) throw new IllegalArgumentException("Image has "+tcxyz[0]+ " frames, not a multiple of sizeZ: "+sizeZ+ " Image: "+input);
        int tp = tcxyz[0]/sizeZ;
        Image[][] imageTC = new Image[tp][tcxyz[1]];
        for (int c = 0; c<tcxyz[1]; ++c) {
            int imgIdx = 0;
            HashMapGetCreate<Integer, List<Image>> planesF = new HashMapGetCreate<>(new HashMapGetCreate.ListFactory());
            for (int z = 0; z<sizeZ; ++z) {
                for (int f = 0;f<tp; ++f) {
                    planesF.get(f).add(reader.openImage(new ImageIOCoordinates(0, c, imgIdx++)));
                }
            }
            TreeMap<Integer, List<Image>> tm = new TreeMap(planesF);
            int f = 0;
            for (List<Image> planes : tm.values()) imageTC[f++][c] = Image.mergeZPlanes(planes);
        }
        ImageWriter.writeToFileTIF(imageTC, -1, destFolder+File.separator+reader.getImageTitle()+".tif");
        //ImageWriter.writeToFile(destFolder, reader.getImageTitle(), ImageFormat.OMETIF, imageTC);
    }
}
