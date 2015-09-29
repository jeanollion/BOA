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
package configuration.parameters;

/**
 *
 * @author jollion
 */
public class ChannelImageParameter extends StructureParameter {
    
    public ChannelImageParameter(String name) {
        this(name, -1);
    }
    
    public ChannelImageParameter(String name, int selectedChannel) {
        super(name, selectedChannel, false, false);
    }
    
    public ChannelImageParameter(String name, int[] selectedChannels) {
        super(name, selectedChannels, false);
    }
    
    @Override
    public String[] getChoiceList() {
        if (getXP()!=null) {
            return getXP().getChannelImagesAsString();
        } else {
            return new String[]{"error: no xp found in tree"};
        }
    }
    
    @Override public void setContentFrom(Parameter other){
        logger.debug("{} set content from: before: {}", getName(), this.selectedIndicies);
        super.setContentFrom(other);
        logger.debug("{} set content from: after: {}", getName(), this.selectedIndicies);
    }
    
}
