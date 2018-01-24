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
package boa.data_structure.image_container;

import boa.image.BoundingBox;
import boa.image.Image;
import boa.data_structure.input_image.InputImage;
import boa.data_structure.input_image.InputImagesImpl;
import java.util.Arrays;
import org.json.simple.JSONObject;

/**
 *
 * @author jollion
 */
public class MemoryImageContainer extends MultipleImageContainer {
    Image[][] imageCT;
    public MemoryImageContainer(Image[][] imageCT) {
        super(imageCT[0][0].getScaleXY(), imageCT[0][0].getScaleZ());
        this.imageCT=imageCT;
    }
    @Override
    public boolean sameContent(MultipleImageContainer other) {
        return true;
    }
    @Override
    public Object toJSONEntry() {
        JSONObject res = new JSONObject();
        return res;
    }

    @Override
    public void initFromJSONEntry(Object jsonEntry) {
    }
    
    @Override
    public int getFrameNumber() {
        return imageCT[0].length;
    }

    @Override
    public int getChannelNumber() {
        return imageCT.length;
    }

    @Override
    public int getSizeZ(int channel) {
        return imageCT[0][0].getSizeZ();
    }

    @Override
    public Image getImage(int timePoint, int channel) {
        return imageCT[channel][timePoint];
    }

    @Override
    public Image getImage(int timePoint, int channel, BoundingBox bounds) {
        return getImage(timePoint, channel).crop(bounds);
    }

    @Override
    public void close() {
        imageCT = null;
    }

    @Override
    public String getName() {
        return "memory image container";
    }

    @Override
    public double getCalibratedTimePoint(int t, int c, int z) {
        return t;
    }

    @Override
    public MultipleImageContainer duplicate() {
        return new MemoryImageContainer(imageCT);
    }

    @Override
    public boolean singleFrame(int channel) {
        return false;
    }
    public InputImagesImpl getInputImages(String position) {
        InputImage[][] inputCT = new InputImage[getChannelNumber()][getFrameNumber()];
        for (int t = 0; t<getFrameNumber(); ++t) {
            for (int c = 0; c<getChannelNumber(); ++c) {
                inputCT[c][t] = new InputImage(c, t, t, position, this, null);
            }
        }
        return new InputImagesImpl(inputCT, 0, null);
    }
}
