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
package boa.data_structure.dao;


/**
 *
 * @author jollion
 */
public class MasterDAOFactory {
    public enum DAOType {Morphium, DBMap, Basic};
    private static DAOType currentType = DAOType.Morphium;

    public static DAOType getCurrentType() {
        return currentType;
    }

    public static void setCurrentType(DAOType currentType) {
        MasterDAOFactory.currentType = currentType;
    }
    
    public static MasterDAO createDAO(String dbName, String dir, DAOType daoType) {
        switch (daoType) {
            case DBMap:
                return new DBMapMasterDAO(dir, dbName);
            case Basic:
                return new BasicMasterDAO();
            default:
                return null;
        }
    }
    public static MasterDAO createDAO(String dbName, String dir) {
        return createDAO(dbName, dir, currentType);
    }
}
