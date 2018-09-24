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
package boa.test_utils;

import static boa.test_utils.TestUtils.logger;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import boa.utils.FileIO;

/**
 *
 * @author Jean Ollion
 */
public class TestRandomAccessFile {
    public static void main(String[] args)  throws Exception {
        String dir = "/data/Images/test.txt";
        String write = "trestrestrestresgfdwgvf";
        RandomAccessFile raf = new RandomAccessFile(new File(dir), "rw");
        FileIO.write(raf, write, true);
        raf.seek(0);
        String read = raf.readLine();
        logger.debug("read: {}, equals? {}", read, write.equals(read));
    }

}
