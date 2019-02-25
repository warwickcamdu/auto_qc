package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.NewImage;
import ij.measure.Calibration;
import ij.plugin.RGBStackMerge;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

class coAlignement {
    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;
    private ImagePlus red = null;
    private ImagePlus green = null;
    private ImagePlus blue = null;
    private microscope[] micro = null;
    private double[] redCentre = null;
    private double[] greenCentre = null;
    private double[] blueCentre = null;
    private double RGDistUnCal;
    private double RBDistUnCal;
    private double GBDistUnCal;
    public double RGDistCal;
    public double RBDistCal;
    public double GBDistCal;
    private double RGRefDist;
    private double RBRefDist;
    private double GBRefDist;
    private Calibration cal = new Calibration();
    private String microSection = "";

    public coAlignement(ImagePlus[] ip, microscope[] conditions) {
        if (ip.length < 2) {
            throw new IllegalArgumentException("coAlignement requires at least 2 ImagePlus.");
        } else if (ip.length != conditions.length) {
            throw new IllegalArgumentException("coAlignement requires the ImagePlus array to be the same size as the microscope array.");
        } else {
            this.red = ip[0];
            this.green = ip[1];
            this.blue = null;
            this.cal = this.red.getCalibration();
            if (ip.length >= 3) {
                this.blue = ip[2];
            }
            if (this.red.getNSlices() == 1) {
                throw new IllegalArgumentException("coAlignement requieres all channels to have the save size and to be of same type.");
            } else if (this.red.getWidth() == this.green.getWidth() && this.red.getHeight() == this.green.getHeight() && this.red.getNSlices() == this.green.getNSlices() && this.red.getBitDepth() == this.green.getBitDepth()) {
                if (ip[2] != null && ip.length == 3 && (this.red.getWidth() != this.blue.getWidth() || this.red.getHeight() != this.blue.getHeight() || this.red.getNSlices() != this.blue.getNSlices() || this.red.getBitDepth() != this.blue.getBitDepth())) {
                    throw new IllegalArgumentException("coAlignement requieres all channels to have the save size and to be of same type.");
                } else {
                    this.micro = conditions;
                    this.microSection = "Microscope: " + microscope.MICRO[this.micro[0].microscope] + "\nWavelengths: " + this.micro[0].wavelength + ", " + this.micro[1].wavelength;
                    if (ip[2] != null) {
                        this.microSection = this.microSection + ", " + this.micro[2].wavelength;
                    }

                    this.microSection = this.microSection + " nm\nNA: " + this.micro[0].NA + "\nSampling rate: " + this.round(this.cal.pixelWidth, 3) + "x" + this.round(this.cal.pixelHeight, 3) + "x" + this.round(this.cal.pixelDepth, 3) + " " + this.cal.getUnit();
                    if (this.micro[0].microscope == 1) {
                        this.microSection = this.microSection + "\nPinhole: " + this.micro[0].pinhole + " Airy Units";
                    }

                    this.getCentresAndDist();
                }
            } else {
                throw new IllegalArgumentException("coAlignement requieres all channels to have the save size and to be of same type.");
            }
        }
    }

    private void getCentresAndDist() {

        this.redCentre = (new findCentre()).getAllCoordinates(this.red);

        this.greenCentre = (new findCentre()).getAllCoordinates(this.green);

        this.RGRefDist = this.calcRefDist(this.redCentre, this.greenCentre, this.micro[1]);
        this.RGDistUnCal = this.dist(this.redCentre, this.greenCentre, 1.0D, 1.0D, 1.0D);
        this.RGDistCal = this.dist(this.redCentre, this.greenCentre, this.cal.pixelWidth, this.cal.pixelHeight, this.cal.pixelDepth);

        if (this.blue != null) {

            this.blueCentre = (new findCentre()).getAllCoordinates(this.blue);
            this.RBDistUnCal = this.dist(this.redCentre, this.blueCentre, 1.0D, 1.0D, 1.0D);
            this.RBDistCal = this.dist(this.redCentre, this.blueCentre, this.cal.pixelWidth, this.cal.pixelHeight, this.cal.pixelDepth);
            this.RBRefDist = this.calcRefDist(this.redCentre, this.blueCentre, this.micro[2]);
            this.GBDistUnCal = this.dist(this.greenCentre, this.blueCentre, 1.0D, 1.0D, 1.0D);
            this.GBDistCal = this.dist(this.greenCentre, this.blueCentre, this.cal.pixelWidth, this.cal.pixelHeight, this.cal.pixelDepth);
            this.GBRefDist = this.calcRefDist(this.greenCentre, this.blueCentre, this.micro[2]);
        }


    }

    private double dist(double[] centre1, double[] centre2, double calX, double calY, double calZ) {
        return centre1.length == 2 ? Math.sqrt((centre2[0] - centre1[0]) * (centre2[0] - centre1[0]) * calX * calX + (centre2[1] - centre1[1]) * (centre2[1] - centre1[1]) * calY * calY) : Math.sqrt((centre2[0] - centre1[0]) * (centre2[0] - centre1[0]) * calX * calX + (centre2[1] - centre1[1]) * (centre2[1] - centre1[1]) * calY * calY + (centre2[2] - centre1[2]) * (centre2[2] - centre1[2]) * calZ * calZ);
    }

    private String[][] getPixShiftArray() {
        String[][] output;
        if (this.blue == null) {
            output = new String[][]{{"Shift\n(pix.)", "Red", "Green"}, {"Red (Ref.)", "0\n0\n0", this.round(this.greenCentre[0] - this.redCentre[0], 3) + "\n" + this.round(this.greenCentre[1] - this.redCentre[1], 3) + "\n" + this.round(this.greenCentre[2] - this.redCentre[2], 3)}, {"Green (Ref.)", this.round(this.redCentre[0] - this.greenCentre[0], 3) + "\n" + this.round(this.redCentre[1] - this.greenCentre[1], 3) + "\n" + this.round(this.redCentre[2] - this.greenCentre[2], 3), "0\n0\n0"}, {"Resolutions\n(pix.)", this.round(this.micro[0].resolution[0] / this.cal.pixelWidth, 3) + "\n" + this.round(this.micro[0].resolution[1] / this.cal.pixelHeight, 3) + "\n" + this.round(this.micro[0].resolution[2] / this.cal.pixelDepth, 3), this.round(this.micro[1].resolution[0] / this.cal.pixelWidth, 3) + "\n" + this.round(this.micro[1].resolution[1] / this.cal.pixelHeight, 3) + "\n" + this.round(this.micro[1].resolution[2] / this.cal.pixelDepth, 3)}, {"Centres' coord.", this.round(this.redCentre[0], 1) + "\n" + this.round(this.redCentre[1], 1) + "\n" + this.round(this.redCentre[2], 1), this.round(this.greenCentre[0], 1) + "\n" + this.round(this.greenCentre[1], 1) + "\n" + this.round(this.greenCentre[2], 1)}, {"Titles", this.red.getTitle(), this.green.getTitle()}};
            return output;
        } else {
            output = new String[][]{{"Shift\n(pix.)", "Red", "Green", "Blue"}, {"Red (Ref.)", "0\n0\n0", this.round(this.greenCentre[0] - this.redCentre[0], 3) + "\n" + this.round(this.greenCentre[1] - this.redCentre[1], 3) + "\n" + this.round(this.greenCentre[2] - this.redCentre[2], 3), this.round(this.blueCentre[0] - this.redCentre[0], 3) + "\n" + this.round(this.blueCentre[1] - this.redCentre[1], 3) + "\n" + this.round(this.blueCentre[2] - this.redCentre[2], 3)}, {"Green (Ref.)", this.round(this.redCentre[0] - this.greenCentre[0], 3) + "\n" + this.round(this.redCentre[1] - this.greenCentre[1], 3) + "\n" + this.round(this.redCentre[2] - this.greenCentre[2], 3), "0\n0\n0", this.round(this.blueCentre[0] - this.greenCentre[0], 3) + "\n" + this.round(this.blueCentre[1] - this.greenCentre[1], 3) + "\n" + this.round(this.blueCentre[2] - this.greenCentre[2], 3)}, {"Blue (Ref.)", this.round(this.redCentre[0] - this.blueCentre[0], 3) + "\n" + this.round(this.redCentre[1] - this.blueCentre[1], 3) + "\n" + this.round(this.redCentre[2] - this.blueCentre[2], 3), this.round(this.greenCentre[0] - this.blueCentre[0], 3) + "\n" + this.round(this.greenCentre[1] - this.blueCentre[1], 3) + "\n" + this.round(this.greenCentre[2] - this.blueCentre[2], 3), "0\n0\n0"}, {"Titles", this.red.getTitle(), this.green.getTitle(), this.blue.getTitle()}};
            return output;
        }
    }

    private String[][] getUnCalDistArray() {
        String[][] output;
        if (this.blue == null) {
            output = new String[][]{{"Dist.\n(pix.)", "Red", "Green"}, {"Red", "-", "" + this.round(this.RGDistUnCal, 3)}, {"Green", "" + this.round(this.RGDistUnCal, 3), "-"}, {"Resolutions\n(pix.)", this.round(this.micro[0].resolution[0] / this.cal.pixelWidth, 3) + "\n" + this.round(this.micro[0].resolution[1] / this.cal.pixelHeight, 3) + "\n" + this.round(this.micro[0].resolution[2] / this.cal.pixelDepth, 3), this.round(this.micro[1].resolution[0] / this.cal.pixelWidth, 3) + "\n" + this.round(this.micro[1].resolution[1] / this.cal.pixelHeight, 3) + "\n" + this.round(this.micro[1].resolution[2] / this.cal.pixelDepth, 3)}, {"Centres' coord.", this.round(this.redCentre[0], 1) + "\n" + this.round(this.redCentre[1], 1) + "\n" + this.round(this.redCentre[2], 1), this.round(this.greenCentre[0], 1) + "\n" + this.round(this.greenCentre[1], 1) + "\n" + this.round(this.greenCentre[2], 1)}, {"Titles", this.red.getTitle(), this.green.getTitle()}};
            return output;
        } else {
            output = new String[][]{{"Dist.\n(pix.)", "Red", "Green", "Blue"}, {"Red", "-", "" + this.round(this.RGDistUnCal, 3), "" + this.round(this.RBDistUnCal, 3)}, {"Green", "" + this.round(this.RGDistUnCal, 3), "-", "" + this.round(this.GBDistUnCal, 3)}, {"Blue", "" + this.round(this.RBDistUnCal, 3), "" + this.round(this.GBDistUnCal, 3), "-"}, {"Resolutions\n(pix.)", this.round(this.micro[0].resolution[0] / this.cal.pixelWidth, 3) + "\n" + this.round(this.micro[0].resolution[1] / this.cal.pixelHeight, 3) + "\n" + this.round(this.micro[0].resolution[2] / this.cal.pixelDepth, 3), this.round(this.micro[1].resolution[0] / this.cal.pixelWidth, 3) + "\n" + this.round(this.micro[1].resolution[1] / this.cal.pixelHeight, 3) + "\n" + this.round(this.micro[1].resolution[2] / this.cal.pixelDepth, 3), this.round(this.micro[2].resolution[0] / this.cal.pixelWidth, 3) + "\n" + this.round(this.micro[2].resolution[1] / this.cal.pixelHeight, 3) + "\n" + this.round(this.micro[2].resolution[2] / this.cal.pixelDepth, 3)}, {"Centres' coord.", this.round(this.redCentre[0], 1) + "\n" + this.round(this.redCentre[1], 1) + "\n" + this.round(this.redCentre[2], 1), this.round(this.greenCentre[0], 1) + "\n" + this.round(this.greenCentre[1], 1) + "\n" + this.round(this.greenCentre[2], 1), this.round(this.blueCentre[0], 1) + "\n" + this.round(this.blueCentre[1], 1) + "\n" + this.round(this.blueCentre[2], 1)}, {"Titles", this.red.getTitle(), this.green.getTitle(), this.blue.getTitle()}};
            return output;
        }
    }

    private String[][] getCalDistArray() {
        String[][] output;
        if (this.blue == null) {
            output = new String[][]{{"Dist.\n(Ref. dist.)\n" + this.cal.getUnit(), "Red", "Green"}, {"Red", "-", this.round(this.RGDistCal, 3) + "\n(" + this.round(this.RGRefDist, 3) + ")"}, {"Green", this.round(this.RGDistCal, 3) + "\n(" + this.round(this.RGRefDist, 3) + ")", "-"}, {"Resolutions\n(" + this.cal.getUnit() + ")", this.round(this.micro[0].resolution[0], 3) + "\n" + this.round(this.micro[0].resolution[1], 3) + "\n" + this.round(this.micro[0].resolution[2], 3), this.round(this.micro[1].resolution[0], 3) + "\n" + this.round(this.micro[1].resolution[1], 3) + "\n" + this.round(this.micro[1].resolution[2], 3)}, {"Centres' coord.", this.round(this.redCentre[0], 1) + "\n" + this.round(this.redCentre[1], 1) + "\n" + this.round(this.redCentre[2], 1), this.round(this.greenCentre[0], 1) + "\n" + this.round(this.greenCentre[1], 1) + "\n" + this.round(this.greenCentre[2], 1)}, {"Titles", this.red.getTitle(), this.green.getTitle()}};
            return output;
        } else {
            output = new String[][]{{"Dist.\n(Ref. dist.)\n" + this.cal.getUnit(), "Red", "Green", "Blue"}, {"Red", "-", this.round(this.RGDistCal, 3) + "\n(" + this.round(this.RGRefDist, 3) + ")", this.round(this.RBDistCal, 3) + "\n(" + this.round(this.RBRefDist, 3) + ")"}, {"Green", this.round(this.RGDistCal, 3) + "\n(" + this.round(this.RGRefDist, 3) + ")", "-", this.round(this.GBDistCal, 3) + "\n(" + this.round(this.GBRefDist, 3) + ")"}, {"Blue", this.round(this.RBDistCal, 3) + "\n(" + this.round(this.RBRefDist, 3) + ")", this.round(this.GBDistCal, 3) + "\n(" + this.round(this.GBRefDist, 3) + ")", "-"}, {"Resolutions\n(" + this.cal.getUnit() + ")", this.round(this.micro[0].resolution[0], 3) + "\n" + this.round(this.micro[0].resolution[1], 3) + "\n" + this.round(this.micro[0].resolution[2], 3), this.round(this.micro[1].resolution[0], 3) + "\n" + this.round(this.micro[1].resolution[1], 3) + "\n" + this.round(this.micro[1].resolution[2], 3), this.round(this.micro[2].resolution[0], 3) + "\n" + this.round(this.micro[2].resolution[1], 3) + "\n" + this.round(this.micro[2].resolution[2], 3)}, {"Centres' coord.", this.round(this.redCentre[0], 1) + "\n" + this.round(this.redCentre[1], 1) + "\n" + this.round(this.redCentre[2], 1), this.round(this.greenCentre[0], 1) + "\n" + this.round(this.greenCentre[1], 1) + "\n" + this.round(this.greenCentre[2], 1), this.round(this.blueCentre[0], 1) + "\n" + this.round(this.blueCentre[1], 1) + "\n" + this.round(this.blueCentre[2], 1)}, {"Titles", this.red.getTitle(), this.green.getTitle(), this.blue.getTitle()}};
            return output;
        }
    }

    public void saveData(String path, String filename) {
        BufferedWriter out = null;

        try {
            out = new BufferedWriter(new FileWriter(path + filename + ".xls"));
            out.write("Pixel shift");
            out.newLine();
            this.saveArray(this.getPixShiftArray(), out);
            out.newLine();
            out.write("Uncalibrated distances (in pixels)");
            out.newLine();
            this.saveArray(this.getUnCalDistArray(), out);
            out.newLine();
            out.write("Calibrated distances (" + this.cal.getUnit() + ")");
            out.newLine();
            this.saveArray(this.getCalDistArray(), out);
            out.close();
        } catch (IOException var5) {
            Logger.getLogger(coAlignement.class.getName()).log(Level.SEVERE, (String)null, var5);
        }

    }

    private void saveArray(String[][] array, BufferedWriter out) {
        try {
            for(int j = 0; j < array[0].length; ++j) {
                String line = "";

                for (String[] strings : array) {
                    line = line + strings[j].replaceAll("\n", " ") + "\t";
                }

                out.write(line);
                out.newLine();
            }
        } catch (IOException var6) {
            Logger.getLogger(coAlignement.class.getName()).log(Level.SEVERE, (String)null, var6);
        }

    }

    public ImagePlus getSideView() {
        sideViewGenerator svg = new sideViewGenerator();
        ImagePlus redView = svg.getPanelView(this.red, 1, true, true, 5, true, this.redCentre, 5);
        ImagePlus greenView = svg.getPanelView(this.green, 1, true, true, 5, true, this.greenCentre, 5);
        ImagePlus blueView = null;
        if (this.blue != null) {
            blueView = svg.getPanelView(this.blue, 1, true, true, 5, true, this.blueCentre, 5);
        } else {
            ImagePlus dummyBlue = NewImage.createImage("blue", this.red.getWidth(), this.red.getHeight(), this.red.getNSlices(), this.red.getBitDepth(), 1);
            dummyBlue.setCalibration(this.cal);
            blueView = svg.getPanelView(dummyBlue, 1, true, true, 5, false, (double[])null, 5);
            dummyBlue = null;
        }

        ImageStack is = (new RGBStackMerge()).mergeStacks(redView.getWidth(), redView.getHeight(), 1, redView.getImageStack(), greenView.getImageStack(), blueView.getImageStack(), false);
        return new ImagePlus("Co-alignement side-view", is);
    }

    private double calcRefDist(double[] coordA, double[] coordB, microscope micro) {
        double x = (coordB[0] - coordA[0]) * this.cal.pixelWidth;
        double y = (coordB[1] - coordA[1]) * this.cal.pixelHeight;
        double z = (coordB[2] - coordA[2]) * this.cal.pixelDepth;
        double distXY = Math.sqrt(x * x + y * y);
        double distXYZ = Math.sqrt(distXY * distXY + z * z);
        double theta = 0.0D;
        if (distXYZ != 0.0D) {
            theta = Math.acos(z / distXYZ);
        }

        double phi = 1.5707963267948966D;
        if (distXY != 0.0D) {
            phi = Math.acos(x / distXY);
        }

        double xRef = micro.resolution[0] * Math.sin(theta) * Math.cos(phi);
        double yRef = micro.resolution[1] * Math.sin(theta) * Math.sin(phi);
        double zRef = micro.resolution[2] * Math.cos(theta);
        return Math.sqrt(xRef * xRef + yRef * yRef + zRef * zRef);
    }

    private double round(double nb2round, int nbOfDigits) {
        return (double)Math.round(nb2round * Math.pow(10.0D, (double)nbOfDigits)) / Math.pow(10.0D, (double)nbOfDigits);
    }
}

