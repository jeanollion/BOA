package boa.image.processing;

import boa.gui.imageInteraction.ImageWindowManagerFactory;
import ij.IJ;
import ij.ImageStack;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import boa.image.Image;
import boa.image.ImageFloat;
import boa.image.ImageInteger;
import boa.image.ImageMask;
import java.util.logging.Level;
import java.util.logging.Logger;

/* 
Modified from: in  order to take in acount z-anisotropy & borders
Bob Dougherty 8/8/2006
Saito-Toriwaki algorithm for Euclidian Distance Transformation.
Direct application of Algorithm 1.
Version S1A: lower memory usage.
Version S1A.1 A fixed indexing bug for 666-bin data set
Version S1A.2 Aug. 9, 2006.  Changed noResult value.
Version S1B Aug. 9, 2006.  Faster.
Version S1B.1 Sept. 6, 2006.  Changed comments.
Version S1C Oct. 1, 2006.  Option for inverse case.
Fixed inverse behavior in y and z directions.
Version D July 30, 2007.  Multithread processing for step 2.

This version assumes the input stack is already in memory, 8-bit, and
outputs to a new 32-bit stack.  Versions that are more stingy with memory
may be forthcoming.

License:
Copyright (c) 2006, OptiNav, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.
Neither the name of OptiNav, Inc. nor the names of its contributors
may be used to endorse or promote products derived from this software
without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

 */
public class EDT {
    public static ImageFloat transform(ImageMask mask, boolean insideMask, float scaleXY, float scaleZ, int nbCPUs) {
        try {
            return new EDT().run(mask, insideMask, scaleXY, scaleZ, nbCPUs);
        } catch (Exception ex) {
            Logger.getLogger(EDT.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
    
    protected ImageFloat run(ImageMask mask, boolean insideMask, float scaleXY, float scaleZ , int nbCPUs) throws Exception {
        int w = mask.getSizeX();
        int h = mask.getSizeY();
        int d = mask.getSizeZ();
        float scale=mask.getSizeZ()>1?scaleZ/scaleXY:1;
        int nThreads = nbCPUs;
        ImageFloat res = new ImageFloat("EDT of: "+mask.getName(), mask);
        float[][] s = res.getPixelArray();
        float[] sk;
        //Transformation 1.  Use s to store g.
        Step1Thread[] s1t = new Step1Thread[nThreads];
        for (int thread = 0; thread < nThreads; thread++) {
            s1t[thread] = new Step1Thread(thread, nThreads, w, h, d, s, mask, insideMask);
            s1t[thread].start();
        }
        try {
            for (int thread = 0; thread < nThreads; thread++) {
                s1t[thread].join();
            }
        } catch (InterruptedException ie) {
            IJ.error("A thread was interrupted in step 1 .");
        }
        //Transformation 2.  g (in s) -> h (in s)
        Step2Thread[] s2t = new Step2Thread[nThreads];
        for (int thread = 0; thread < nThreads; thread++) {
            s2t[thread] = new Step2Thread(thread, nThreads, w, h, d, insideMask, s);
            s2t[thread].start();
        }
        try {
            for (int thread = 0; thread < nThreads; thread++) {
                s2t[thread].join();
            }
        } catch (InterruptedException ie) {
            IJ.error("A thread was interrupted in step 2 .");
        }
        if (mask.getSizeZ()>1) { //3D case
            //Transformation 3. h (in s) -> s
            Step3Thread[] s3t = new Step3Thread[nThreads];
            for (int thread = 0; thread < nThreads; thread++) {
                s3t[thread] = new Step3Thread(thread, nThreads, w, h, d, s, mask, insideMask, scale);
                s3t[thread].start();
            }
            try {
                for (int thread = 0; thread < nThreads; thread++) {
                    s3t[thread].join();
                }
            } catch (InterruptedException ie) {
                IJ.error("A thread was interrupted in step 3 .");
            }
        }
        //Find the largest distance for scaling
        //Also fill in the background values.
        float distMax = 0;
        int wh = w * h;
        float dist;
        for (int k = 0; k < d; k++) {
            sk = s[k];
            for (int ind = 0; ind < wh; ind++) {
                if (mask.insideMask(ind, k)!=insideMask) { //xor
                    sk[ind] = 0;
                } else {
                    dist = (float) Math.sqrt(sk[ind]) * scaleXY;
                    sk[ind] = dist;
                    distMax = (dist > distMax) ? dist : distMax;
                }
            }
        }
        //res.setMinAndMax(0, distMax);
        return res;
    }

    class Step1Thread extends Thread {
        int thread, nThreads, w, h, d;
        float[][] s;
        ImageMask mask;
        boolean insideMask;
        
        public Step1Thread(int thread, int nThreads, int w, int h, int d, float[][] s, ImageMask mask, boolean insideMask) {
            this.thread = thread;
            this.nThreads = nThreads;
            this.w = w;
            this.h = h;
            this.d = d;
            this.mask = mask;
            this.insideMask=insideMask;
            this.s = s;
        }

        public void run() {
            float[] sk;
            int n = w;
            if (h > n) n = h;
            if (d > n) n = d;
            
            int noResult = 3 * (n + 1) * (n + 1);
            boolean[] background = new boolean[w];
            float test, min;
            for (int k = thread; k < d; k += nThreads) {
                sk = s[k];
                for (int j = 0; j < h; j++) {
                    if (insideMask) for (int i = 0; i < w; i++) background[i] = !mask.insideMask(i + w * j, k);
                    else for (int i = 0; i < w; i++) background[i] = mask.insideMask(i + w * j, k);
                    
                    for (int i = 0; i < w; i++) {
                        if (insideMask) {min = Math.min(i+1, w-i); min*=min;} // si insideMask: distance minimale = distance au bord le plus proche + 1
                        else min = noResult;
                        for (int x = i; x < w; x++) {
                            if (background[x]) {
                                test = i - x;
                                test *= test;
                                if (test < min) {
                                    min = test;
                                }
                                break;
                            }
                        }
                        for (int x = i - 1; x >= 0; x--) {
                            if (background[x]) {
                                test = i - x;
                                test *= test;
                                if (test < min) {
                                    min = test;
                                }
                                break;
                            }
                        }
                        sk[i + w * j] = min;
                    }
                }
            }
        }//run
    }//Step1Thread

    class Step2Thread extends Thread {

        int thread, nThreads, w, h, d;
        float[][] s;
        boolean insideMask;
        public Step2Thread(int thread, int nThreads, int w, int h, int d, boolean insideMask, float[][] s) {
            this.thread = thread;
            this.nThreads = nThreads;
            this.w = w;
            this.h = h;
            this.d = d;
            this.s = s;
            this.insideMask=insideMask;
        }

        public void run() {
            float[] sk;
            int n = w;
            if (h > n) n = h;
            if (d > n) n = d;
            int noResult = 3 * (n + 1) * (n + 1);
            float[] tempInt = new float[h];
            float[] tempS = new float[h];
            boolean nonempty;
            float test, min;
            int delta;
            for (int k = thread; k < d; k += nThreads) {
                sk = s[k];
                for (int i = 0; i < w; i++) {
                    nonempty = false;
                    for (int j = 0; j < h; j++) {
                        tempS[j] = sk[i + w * j];
                        if (tempS[j] > 0) {
                            nonempty = true;
                        }
                    }
                    if (nonempty) {
                        for (int j = 0; j < h; j++) {
                            if (insideMask) {min = Math.min(j+1, h-j); min*=min;}
                            else min = noResult;
                            delta = j;
                            for (int y = 0; y < h; y++) {
                                test = tempS[y] + delta * delta--;
                                if (test < min) {
                                    min = test;
                                }
                            }
                            tempInt[j] = min;
                        }
                        for (int j = 0; j < h; j++) {
                            sk[i + w * j] = tempInt[j];
                        }
                    }
                }
            }
        }//run
    }//Step2Thread	

    class Step3Thread extends Thread {
        int thread, nThreads, w, h, d;
        float[][] s;
        ImageMask mask;
        float scaleZ;
        boolean insideMask;
        
        public Step3Thread(int thread, int nThreads, int w, int h, int d, float[][] s, ImageMask mask, boolean insideMask, float scaleZ) {
            this.thread = thread;
            this.nThreads = nThreads;
            this.w = w;
            this.h = h;
            this.d = d;
            this.s = s;
            this.mask = mask;
            this.scaleZ = scaleZ * scaleZ;
            this.insideMask=insideMask;
        }

        public void run() {
            int zStart, zStop, zBegin, zEnd;
            int n = w;
            if (h > n) {
                n = h;
            }
            if (d > n) {
                n = d;
            }
            int noResult = 3 * (n + 1) * (n + 1);
            float[] tempInt = new float[d];
            float[] tempS = new float[d];
            boolean nonempty;
            float test, min;
            int delta;
            for (int j = thread; j < h; j += nThreads) {
                for (int i = 0; i < w; i++) {
                    nonempty = false;
                    for (int k = 0; k < d; k++) {
                        tempS[k] = s[k][i + w * j];
                        if (tempS[k] > 0) {
                            nonempty = true;
                        }
                    }
                    if (nonempty) {
                        zStart = 0;
                        while ((zStart < (d - 1)) && (tempS[zStart] == 0)) {
                            zStart++;
                        }
                        if (zStart > 0) {
                            zStart--;
                        }
                        zStop = d - 1;
                        while ((zStop > 0) && (tempS[zStop] == 0)) {
                            zStop--;
                        }
                        if (zStop < (d - 1)) {
                            zStop++;
                        }

                        for (int k = 0; k < d; k++) {
                            //Limit to the non-background to save time,
                            if (insideMask==mask.insideMask(i+w*j, k)) { //!xor
                                if (insideMask) {min=Math.min(k+1, d-k); min *= min * scaleZ;} //&& d>1
                                else min = noResult;
                                zBegin = zStart;
                                zEnd = zStop;
                                if (zBegin > k) {
                                    zBegin = k;
                                }
                                if (zEnd < k) {
                                    zEnd = k;
                                }
                                delta = (k - zBegin);

                                for (int z = zBegin; z <= zEnd; z++) {
                                    test = tempS[z] + delta * delta-- * scaleZ;
                                    if (test < min) {
                                        min = test;
                                    }
                                    //min = (test < min) ? test : min;
                                }
                                tempInt[k] = min;
                            }
                        }
                        for (int k = 0; k < d; k++) {
                            s[k][i + w * j] = tempInt[k];
                        }
                    }
                }
            }
        }//run
    }//Step2Thread	
}
