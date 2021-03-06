/* 
 * Copyright (C) 2018 Jean Ollion
 *
 * This File is part of BACMMAN
 *
 * BACMMAN is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * BACMMAN is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with BACMMAN.  If not, see <http://www.gnu.org/licenses/>.
 */
package boa.configuration.parameters;

import java.util.function.Consumer;


/**
 *
 * @author Jean Ollion
 * @param <P>
 */
public class BoundedNumberParameter extends NumberParameter<BoundedNumberParameter> {
    Number lowerBound, upperBound;
    
    public BoundedNumberParameter(String name, int decimalPlaces, Number defaultValue, Number lowerBound, Number upperBound) {
        super(name, decimalPlaces, defaultValue);
        this.lowerBound=lowerBound;
        this.upperBound=upperBound;
    }

    public Number getLowerBound() {
        return lowerBound;
    }

    public Number getUpperBound() {
        return upperBound;
    }
    @Override 
    public boolean isValid() {
        if (!super.isValid()) return false;
        return super.isValid() && (lowerBound==null || value.doubleValue()>=lowerBound.doubleValue()) && (upperBound==null || value.doubleValue()<=upperBound.doubleValue());
    }
    @Override public BoundedNumberParameter duplicate() {
        BoundedNumberParameter res = new BoundedNumberParameter(name, decimalPlaces, value, lowerBound, upperBound);
        res.setListeners(listeners);
        res.addValidationFunction(additionalValidation);
        return res;
    }
}
