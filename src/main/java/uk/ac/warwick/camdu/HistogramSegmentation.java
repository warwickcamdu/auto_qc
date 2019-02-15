package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import ij.IJ;
import ij.ImagePlus;
import ij.gui.NewImage;
import ij.process.ImageProcessor;

public class HistogramSegmentation {
    int[] histo;
    int min = 0;
    int max = 0;
    int[] limits;

    public HistogramSegmentation(ImagePlus ip) {
        int bitDepth = ip.getBitDepth();
        if (bitDepth != 8 && bitDepth != 16) {
            throw new IllegalArgumentException("Histo_seg expect a 8- or 16-bits images");
        } else {
            this.max = 0;
            this.min = (int)Math.pow(2.0D, (double)bitDepth);
            this.histo = new int[this.min];

            for(int z = 1; z <= ip.getNSlices(); ++z) {
                ip.setSlice(z);

                for(int y = 0; y < ip.getHeight(); ++y) {
                    for(int x = 0; x < ip.getWidth(); ++x) {
                        int val = ip.getPixel(x, y)[0];
                        this.min = Math.min(this.min, val);
                        this.max = Math.max(this.max, val);
                        int var10002 = this.histo[val]++;
                    }
                }
            }

        }
    }

    public int[] calcLimits(int nClasses, int maxIt, int epsilon, boolean log) {
        double[] means = new double[nClasses];
        this.limits = new int[nClasses + 1];
        this.limits[0] = this.min;
        this.limits[nClasses] = this.max;

        int it;
        for(it = 1; it < nClasses; ++it) {
            this.limits[it] = this.limits[it - 1] + (this.max - this.min) / nClasses;
        }

        it = 0;

        int convFact;
        do {
            int[] oldLimits = (int[])this.limits.clone();

            int i;
            for(i = 0; i < nClasses; ++i) {
                double freq = 0.0D;
                double mean = 0.0D;
                int limLow = this.limits[i];
                int limHigh = i == nClasses - 1 ? this.limits[i + 1] + 1 : this.limits[i + 1];

                for(int j = limLow; j < limHigh; ++j) {
                    int val = this.histo[j];
                    freq += log ? (val != 0 ? Math.log((double)val) : 0.0D) : (double)val;
                    mean += log ? (val != 0 ? Math.log((double)val) * (double)j : 0.0D) : (double)(val * j);
                }

                means[i] = mean / freq;
            }

            for(i = 1; i < nClasses; ++i) {
                this.limits[i] = (int)Math.floor((means[i - 1] + means[i]) / 2.0D);
            }

            convFact = 0;

            for(i = 0; i < nClasses + 1; ++i) {
                convFact += Math.abs(this.limits[i] - oldLimits[i]);
            }

            ++it;
        } while(it < maxIt && convFact > epsilon);

        return this.limits;
    }

    public int[] getLimitsFluo(int nClasses) {
        return this.calcLimits(nClasses, 1000, 0, true);
    }

    public int[] getLimitsTrans(int nClasses) {
        return this.calcLimits(nClasses, 1000, 0, false);
    }

    public int[] getHisto() {
        return this.histo;
    }

    public double getMean(int nClasse) {
        --nClasse;
        if (this.limits == null) {
            throw new IllegalArgumentException("calcLimits has not yet been called.");
        } else if (nClasse >= 0 && nClasse <= this.limits.length - 1) {
            double mean = 0.0D;
            double freq = 0.0D;
            int limLow = this.limits[nClasse];
            int limHigh = nClasse == this.limits.length - 1 ? this.limits[nClasse + 1] + 1 : this.limits[nClasse + 1];

            for(int i = limLow; i < limHigh; ++i) {
                freq += (double)this.histo[i];
                mean += (double)(i * this.histo[i]);
            }

            return mean / freq;
        } else {
            throw new IllegalArgumentException("Class number out of the [1-" + (this.limits.length - 1) + "] range.");
        }
    }

    public double[] getMean() {
        if (this.limits == null) {
            throw new IllegalArgumentException("calcLimits has not yet been called.");
        } else {
            double[] mean = new double[this.limits.length - 1];

            for(int i = 1; i < this.limits.length; ++i) {
                mean[i - 1] = this.getMean(i);
            }

            return mean;
        }
    }

    public int getMedian(int nClasse) {
        --nClasse;
        if (this.limits == null) {
            throw new IllegalArgumentException("calcLimits has not yet been called.");
        } else if (nClasse >= 0 && nClasse <= this.limits.length - 1) {
            int median = 0;
            int nbVal = 0;
            int limLow = this.limits[nClasse];
            int limHigh = nClasse == this.limits.length - 1 ? this.limits[nClasse + 1] + 1 : this.limits[nClasse + 1];

            int currNb;
            for(currNb = limLow; currNb < limHigh; ++currNb) {
                nbVal += this.histo[currNb];
            }

            nbVal /= 2;
            currNb = 0;
            int i = limLow;

            do {
                currNb += this.histo[i];
                median = i++;
            } while(currNb < nbVal && i <= limHigh);

            return median;
        } else {
            throw new IllegalArgumentException("Class number out of the [1-" + (this.limits.length - 1) + "] range.");
        }
    }

    public int[] getMedian() {
        if (this.limits == null) {
            throw new IllegalArgumentException("calcLimits has not yet been called.");
        } else {
            int[] median = new int[this.limits.length - 1];

            for(int i = 1; i < this.limits.length; ++i) {
                median[i - 1] = this.getMedian(i);
            }

            return median;
        }
    }

    public int getNb(int nClasse) {
        --nClasse;
        if (this.limits == null) {
            throw new IllegalArgumentException("calcLimits has not yet been called.");
        } else if (nClasse >= 0 && nClasse <= this.limits.length - 1) {
            int nb = 0;
            int limLow = this.limits[nClasse];
            int limHigh = nClasse == this.limits.length - 1 ? this.limits[nClasse + 1] + 1 : this.limits[nClasse + 1];

            for(int i = limLow; i < limHigh; ++i) {
                nb += this.histo[i];
            }

            return nb;
        } else {
            throw new IllegalArgumentException("Class number out of the [1-" + (this.limits.length - 1) + "] range.");
        }
    }

    public int[] getNb() {
        if (this.limits == null) {
            throw new IllegalArgumentException("calcLimits has not yet been called.");
        } else {
            int[] nb = new int[this.limits.length - 1];

            for(int i = 1; i < this.limits.length; ++i) {
                nb[i - 1] = this.getNb(i);
            }

            return nb;
        }
    }

    public int getIntegratedInt(int nClasse) {
        --nClasse;
        if (this.limits == null) {
            throw new IllegalArgumentException("calcLimits has not yet been called.");
        } else if (nClasse >= 0 && nClasse <= this.limits.length - 1) {
            int intInt = 0;
            int limLow = this.limits[nClasse];
            int limHigh = nClasse == this.limits.length - 1 ? this.limits[nClasse + 1] + 1 : this.limits[nClasse + 1];

            for(int i = limLow; i < limHigh; ++i) {
                intInt += i * this.histo[i];
            }

            return intInt;
        } else {
            throw new IllegalArgumentException("Class number out of the [1-" + (this.limits.length - 1) + "] range.");
        }
    }

    public int[] getIntegratedInt() {
        if (this.limits == null) {
            throw new IllegalArgumentException("calcLimits has not yet been called.");
        } else {
            int[] intInt = new int[this.limits.length - 1];

            for(int i = 1; i < this.limits.length; ++i) {
                intInt[i - 1] = this.getIntegratedInt(i);
            }

            return intInt;
        }
    }

    public ImagePlus getsegmentedImage(ImagePlus ip) {
        if (this.limits == null) {
            throw new IllegalArgumentException("calcLimits has not yet been called.");
        } else {
            ImagePlus dest = IJ.createImage("SegImg_" + ip.getTitle(), ip.getBitDepth() + "-bit", ip.getWidth(), ip.getHeight(), ip.getNSlices());

            for(int z = 1; z <= ip.getNSlices(); ++z) {
                ip.setSlice(z);
                dest.setSlice(z);
                ImageProcessor oriProc = ip.getProcessor();
                ImageProcessor destProc = dest.getProcessor();

                for(int y = 0; y < ip.getHeight(); ++y) {
                    for(int x = 0; x < ip.getWidth(); ++x) {
                        int val = oriProc.get(x, y);
                        boolean wasChanged = false;

                        for(int borne = 0; borne < this.limits.length - 1; ++borne) {
                            if (val >= this.limits[borne] && val < this.limits[borne + 1]) {
                                destProc.set(x, y, borne + 1);
                                wasChanged = true;
                            }
                        }

                        if (!wasChanged) {
                            destProc.set(x, y, this.limits.length - 1);
                        }
                    }
                }
            }

            dest.setSlice(1);
            dest.setDisplayRange(0.0D, (double)(this.limits.length - 1));
            dest.updateAndDraw();
            return dest;
        }
    }

    public ImagePlus getsegmentedImage(ImagePlus ip, int nClass) {
        if (this.limits == null) {
            throw new IllegalArgumentException("calcLimits has not yet been called.");
        } else if (nClass >= 0 && nClass < this.limits.length) {
            ImagePlus dest = NewImage.createImage("SegImg_class_" + nClass + "_" + ip.getTitle(), ip.getWidth(), ip.getHeight(), ip.getNSlices(), 8, 1);

            for(int z = 1; z <= ip.getNSlices(); ++z) {
                ip.setSlice(z);
                dest.setSlice(z);
                ImageProcessor oriProc = ip.getProcessor();
                ImageProcessor destProc = dest.getProcessor();

                for(int y = 0; y < ip.getHeight(); ++y) {
                    for(int x = 0; x < ip.getWidth(); ++x) {
                        int val = oriProc.get(x, y);
                        boolean wasChanged = false;
                        if (val >= this.limits[nClass]) {
                            destProc.set(x, y, 255);
                            wasChanged = true;
                        }

                        if (!wasChanged) {
                            destProc.set(x, y, 0);
                        }
                    }
                }
            }

            dest.setSlice(1);
            dest.setDisplayRange(0.0D, 255.0D);
            dest.updateAndDraw();
            return dest;
        } else {
            throw new IllegalArgumentException("nClass out of bounds.");
        }
    }

    public void doSegmentation(ImagePlus ip) {
        for(int z = 1; z <= ip.getNSlices(); ++z) {
            ip.setSlice(z);
            ImageProcessor iproc = ip.getProcessor();

            for(int y = 0; y < ip.getHeight(); ++y) {
                for(int x = 0; x < ip.getWidth(); ++x) {
                    int val = iproc.get(x, y);
                    boolean wasChanged = false;

                    for(int borne = 0; borne < this.limits.length - 1; ++borne) {
                        if (val >= this.limits[borne] && val < this.limits[borne + 1]) {
                            iproc.set(x, y, borne + 1);
                            wasChanged = true;
                        }
                    }

                    if (!wasChanged) {
                        iproc.set(x, y, this.limits.length - 1);
                    }
                }
            }
        }

        ip.setSlice(1);
        ip.setDisplayRange(0.0D, (double)(this.limits.length - 1));
        ip.updateAndDraw();
    }
}

