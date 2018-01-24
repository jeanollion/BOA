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
package boa.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author jollion
 */
public class MultipleException extends RuntimeException {
    final private List<Pair<String, Exception>> exceptions;
    public MultipleException(List<Pair<String, Exception>> exceptions) {
        this.exceptions=exceptions;
    }
    public MultipleException() {
        this.exceptions=new ArrayList<>();
    }
    public void addExceptions(Pair<String, Exception>... ex) {
        exceptions.addAll(Arrays.asList(ex));
    }
    public void addExceptions(Collection<Pair<String, Exception>> ex) {
        exceptions.addAll(ex);
    }
    public List<Pair<String, Exception>> getExceptions() {
        return exceptions;
    }
    public boolean isEmpty() {
        return exceptions.isEmpty();
    }
}
