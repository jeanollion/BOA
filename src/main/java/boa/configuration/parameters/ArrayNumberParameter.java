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
package boa.configuration.parameters;

import java.util.Collections;

/**
 *
 * @author jollion
 */
public class ArrayNumberParameter extends SimpleListParameter<BoundedNumberParameter> {
    boolean sorted;
    public ArrayNumberParameter(String name, int unmutableIndex, BoundedNumberParameter childInstance) {
        super(name, unmutableIndex, childInstance);
        newInstanceNameFunction = i->Integer.toString(i);
    }
    public ArrayNumberParameter setSorted(boolean sorted) {
        this.sorted=sorted;
        return this;
    }
    @Override 
    public BoundedNumberParameter createChildInstance() {
        BoundedNumberParameter p = super.createChildInstance();
        p.addListener(o->{if (sorted) sort();});
        return p;
    }
    public void sort() {
        Collections.sort(children, (n1, n2)->Double.compare(n1.getValue().doubleValue(), n2.getValue().doubleValue()));
    }
    
    public double[] getArrayDouble() {
        double[] res = new double[this.getChildCount()];
        int idx = 0;
        for (BoundedNumberParameter p : getChildren()) res[idx++] = p.getValue().doubleValue();
        return res;
    }
    public void setValue(double... values) {
        if (unMutableIndex>=0 && values.length<=unMutableIndex) throw new IllegalArgumentException("Min number of values: "+this.unMutableIndex+1);
        this.setChildrenNumber(values.length);
        for (int i = 0; i<values.length; ++i) {
            this.getChildAt(i).setValue(values[i]);
        }
    }
    public int[] getArrayInt() {
        int[] res = new int[this.getChildCount()];
        int idx = 0;
        for (BoundedNumberParameter p : getChildren()) res[idx++] = p.getValue().intValue();
        return res;
    }
    public Object getValue() {
        if (this.getChildCount()==1) {
            if (this.childInstance.decimalPlaces==0) return this.getChildAt(0).getValue().intValue();
            else return this.getChildAt(0).getValue().doubleValue();
        }
        if (this.childInstance.decimalPlaces==0) return getArrayInt();
        else return getArrayDouble();
    }
}
