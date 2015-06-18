/*
 * Copyright (C) 2015 ImageJ
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
package morphiaTest2;

import java.util.ArrayList;
import org.mongodb.morphia.annotations.Embedded;
import org.mongodb.morphia.annotations.Transient;

/**
 *
 * @author jollion
 */
@Embedded
public class ClassEmbedded {
    private String name;
    private int i = 1;
    private ClassEmbedded[] embArray;
    private ArrayList<ClassEmbedded> embArrayList;
    @Transient
    private int trans=2;
    private ClassEmbedded(){}
    public ClassEmbedded(String name) {
        this.name = name;
        
    }
    
    public void setEmb() {
        this.embArray=new ClassEmbedded[]{new ClassEmbedded("ea1"), new ClassEmbedded("ea2")};
        this.embArrayList=new ArrayList<ClassEmbedded>(4);
        embArrayList.add(new ClassEmbedded("eal1"));
        embArrayList.add(new ClassEmbedded("eal2"));
        embArrayList.add(new ClassEmbedded("eal3"));
    }

    public String getName() {
        return name;
    }

    public int getI() {
        return i;
    }

    public ClassEmbedded[] getEmbArray() {
        return embArray;
    }

    public ArrayList<ClassEmbedded> getEmbArrayList() {
        return embArrayList;
    }

    public int getTrans() {
        return trans;
    }
}
