/*
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

import java.util.List;

/**
 *
 * @author jollion
 */
public interface ListParameter<T extends Parameter> extends ContainerParameter { //<T extends Parameter>
    public abstract T createChildInstance();
    public List<T> getChildren();
    /**
     * 
     * @param child to be inserted in the list
     * @return the same instance of ListParameter
     */
    public Class<T> getChildClass();
    public void insert(T... child);
    public void removeAllElements();
    public int getUnMutableIndex();
    public boolean isDeactivatable();
    public void setActivatedAll(boolean activated);
    public List<T> getActivatedChildren();
    public T getChildByName(String name);
}
