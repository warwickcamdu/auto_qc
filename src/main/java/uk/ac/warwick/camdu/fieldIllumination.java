package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import ij.IJ;
import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.NewImage;
import ij.gui.Plot;
import ij.gui.ProfilePlot;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import java.awt.Color;
import java.awt.Font;
import java.util.Vector;


class fieldIllumination {
    private ImagePlus ip = null;
    private Calibration cal = null;
    private int w = 0;
    private int h = 0;
    private double[][] diag_TL_BR = null;
    private double[][] diag_TR_BL = null;
    private double[][] horiz = null;
    private double[][] vert = null;
    private double xCent = 0.0D;
    private double yCent = 0.0D;
    private double xMax = 0.0D;
    private double yMax = 0.0D;
    private double xCent100 = 0.0D;
    private double yCent100 = 0.0D;
    private double distInt = 0.0D;
    private double distMax = 0.0D;
    private double dist100 = 0.0D;
    @SuppressWarnings("unchecked")
    private final Vector<fieldIlluminationArray> remarkInt = new Vector();
    private static final String[] lineHead = new String[]{"Top-left corner", "Top-right corner", "Bottom-left corner", "Bottom-right corner", "Upper bound, middle pixel", "Lower bound, middle pixel", "Left bound, middle pixel", "Right bound, middle pixel"};

    public fieldIllumination(ImagePlus im) {
        this.ip = im;
        if (this.ip == null) {
            IJ.error("Please, open an image first...");
        } else {
            this.w = this.ip.getWidth();
            this.h = this.ip.getHeight();
            this.cal = this.ip.getCalibration();
            this.ip.setCalibration((Calibration)null);
            ImageStatistics is = this.ip.getStatistics(64);
            this.xCent = is.xCenterOfMass;
            this.yCent = is.yCenterOfMass;
            this.ip.setCalibration(this.cal);
            this.distInt = dataTricks.dist(new double[]{this.xCent, this.yCent}, new double[]{(double)(this.w / 2), (double)(this.h / 2)}, this.cal);
            this.diag_TL_BR = this.getProfile(this.ip, new Line(0, 0, this.w - 1, this.h - 1));
            this.diag_TR_BL = this.getProfile(this.ip, new Line(this.w - 1, 0, 0, this.h - 1));
            this.horiz = this.getProfile(this.ip, new Line(0, this.h / 2 - 1, this.w - 1, this.h / 2 - 1));
            this.vert = this.getProfile(this.ip, new Line(this.w / 2 - 1, 0, this.w / 2 - 1, this.h - 1));
            int[][] coords = new int[][]{new int[2], {this.w - 1, 0}, {0, this.h - 1}, {this.w - 1, this.h - 1}, {this.w / 2, 0}, {this.w / 2, this.h - 1}, {0, this.h / 2}, {this.w - 1, this.h / 2}};

            for(int i = 0; i < lineHead.length; ++i) {
                fieldIlluminationArray fia = new fieldIlluminationArray();
                fia.name = lineHead[i];
                fia.coord = coords[i];
                fia.intensity = this.ip.getPixel(coords[i][0], coords[i][1])[0];
                double max = this.ip.getStatistics(16).max;
                fia.relativeInt = fia.intensity/max;
                this.remarkInt.add(fia);
            }

        }
    }

    private ImagePlus getPattern(int stepWidth, int barWidth) {
        ImageProcessor iproc = NewImage.createImage("", this.w, this.h, 1, 8, 1).getProcessor();
        double max = this.ip.getStatistics(16).max;

        for(int y = 0; y < this.h; ++y) {
            for(int x = 0; x < this.w; ++x) {
                int currInt = this.ip.getPixel(x, y)[0];
                if ((double)currInt == max) {
                    this.xMax = (double)x;
                    this.yMax = (double)y;
                }

                iproc.set(x, y, (int)((double)currInt / max * 100.0D / (double)stepWidth) * stepWidth);
            }
        }

        this.distMax = dataTricks.dist(new double[]{this.xMax, this.yMax}, new double[]{(double)(this.w / 2), (double)(this.h / 2)}, this.cal);
        iproc.setThreshold(100.0D, 100.0D, 2);
        ImagePlus out = new ImagePlus("Pattern from " + this.ip.getTitle(), iproc);
        ImageStatistics is = out.getStatistics(288);
        iproc.resetThreshold();
        this.xCent100 = is.xCentroid;
        this.yCent100 = is.yCentroid;
        this.dist100 = dataTricks.dist(new double[]{this.xCent100, this.yCent100}, new double[]{(double)(this.w / 2), (double)(this.h / 2)}, this.cal);
        fieldIlluminationArray fia = new fieldIlluminationArray();
        fia.name = "Maximum found at (" + (int)this.xMax + "," + (int)this.yMax + ")";
        fia.coord = new int[]{(int)this.xMax, (int)this.yMax};
        fia.intensity = (int)max;
        this.remarkInt.add(0, fia);

        for(int i = 0; i < this.remarkInt.size(); ++i) {
            ((fieldIlluminationArray)this.remarkInt.elementAt(i)).relativeInt = (double)((fieldIlluminationArray)this.remarkInt.elementAt(i)).intensity / max;
        }

        iproc.setFont(new Font("SansSerif", 1, this.w / 35));
        iproc.setColor(Color.white);
        double slope = (double)(this.h - 1) / (double)(this.w - 1);
        int prevX = this.w - 1;
        int prevY = this.h - 1;
        int refInt = iproc.get(this.w - 1, this.h - 1);

        for(int i = this.w - 1; i >= 0; i -= this.w / 35) {
            int currInt = iproc.get(i, (int)((double)i * slope));
            if (currInt != refInt) {
                String label = refInt - stepWidth + "-" + refInt + "%";
                int y = (int)((double)i * slope);
                iproc.drawString(label, (prevX + i - iproc.getStringWidth(label)) / 2, (prevY + y) / 2 + iproc.getFont().getSize());
                refInt = currInt;
                prevX = i;
                prevY = y;
            }
        }

        imageTricks.addScaleBar(iproc, this.ip.getCalibration(), 1, barWidth);
        imageTricks.applyFire(iproc);
        return new ImagePlus("Pattern from " + this.ip.getTitle(), iproc);
    }

    @SuppressWarnings("deprecation")
    private ImagePlus getProfilesImage() {
        double min = Math.min(Math.min(Math.min(dataTricks.min(this.diag_TL_BR[1]), dataTricks.min(this.diag_TR_BL[1])), dataTricks.min(this.horiz[1])), dataTricks.min(this.vert[1]));
        double max = Math.max(Math.max(Math.max(dataTricks.max(this.diag_TL_BR[1]), dataTricks.max(this.diag_TR_BL[1])), dataTricks.max(this.horiz[1])), dataTricks.max(this.vert[1]));
        Plot plot = new Plot("Field illumination profiles", "Distance to image center", "Intensity", this.diag_TL_BR[0], this.diag_TL_BR[1]);
        plot.setLimits(this.diag_TL_BR[0][0], this.diag_TL_BR[0][this.diag_TL_BR[0].length - 1], min, max);
        plot.setSize(600, 400);
        plot.setColor(imageTricks.COLORS[0]);
        plot.draw();
        plot.setColor(imageTricks.COLORS[1]);
        plot.addPoints(this.diag_TR_BL[0], this.diag_TR_BL[1], 2);
        plot.setColor(imageTricks.COLORS[2]);
        plot.addPoints(this.horiz[0], this.horiz[1], 2);
        plot.setColor(imageTricks.COLORS[3]);
        plot.addPoints(this.vert[0], this.vert[1], 2);
        double[][] line = new double[][]{{0.0D, 0.0D}, {0.0D, max}};
        plot.setColor(Color.black);
        plot.addPoints(line[0], line[1], 2);
        plot.draw();
        String label = "Top-left/bottom-right: " + imageTricks.COLOR_NAMES[0] + "\nTop-right/bottom-left: " + imageTricks.COLOR_NAMES[1] + "\nHorizontale: " + imageTricks.COLOR_NAMES[2] + "\nVerticale: " + imageTricks.COLOR_NAMES[3];
        plot.setColor(Color.black);
        plot.addLabel(0.05D, 0.85D, label);
        return plot.getImagePlus();
    }

    private String getStringProfiles() {
        String out = "distance (µm)\tTop-left/bottom-right\tdistance (µm)\tTop-right/bottom-left\tdistance (µm)\tHorizontale\tdistance (µm)\tnVerticale\n";

        for(int i = 0; i < this.diag_TL_BR[0].length; ++i) {
            out = out + this.diag_TL_BR[0][i] + "\t" + this.diag_TL_BR[1][i] + "\t" + this.diag_TR_BL[0][i] + "\t" + this.diag_TR_BL[1][i];
            if (i < this.horiz[0].length) {
                out = out + "\t" + this.horiz[0][i] + "\t" + this.horiz[1][i];
            } else {
                out = out + "\t\t";
            }

            if (i < this.vert[0].length) {
                out = out + "\t" + this.vert[0][i] + "\t" + this.vert[1][i];
            } else {
                out = out + "\t\t";
            }

            out = out + "\n";
        }

        return out;
    }

    String getStringData() {
        String out = "\tImage centre\tCentre of intensity\tCentre of the max intensity\tCentre of the 100% zone\nCoordinates\t(" + dataTricks.round((double)(this.w / 2), 3) + ", " + dataTricks.round((double)(this.h / 2), 3) + ")\t(" + dataTricks.round(this.xCent, 3) + ", " + dataTricks.round(this.yCent, 3) + ")\t(" + dataTricks.round(this.xMax, 3) + ", " + dataTricks.round(this.yMax, 3) + ")\t(" + dataTricks.round(this.xCent100, 3) + ", " + dataTricks.round(this.yCent100, 3) + ")\n" + "Distance to image centre\t\t" + dataTricks.round(this.distInt, 3) + "µm\t" + dataTricks.round(this.distMax, 3) + "µm\t" + dataTricks.round(this.dist100, 3) + "µm\n\n" + "Location\tIntensity\tIntensity relative to max\n";

        for(int i = 0; i < this.remarkInt.size(); ++i) {
            out = out + ((fieldIlluminationArray)this.remarkInt.elementAt(i)).toString() + "\n";
        }

        return out;
    }

    public String[][] getCenterTableForReport() {
        return new String[][]{{"", "Coordinates", "Distance to image centre"}, {"Image centre", "(" + dataTricks.round((double)(this.w / 2), 3) + ", " + dataTricks.round((double)(this.h / 2), 3) + ")", ""}, {"Centre of intensity", "(" + dataTricks.round(this.xCent, 3) + ", " + dataTricks.round(this.yCent, 3) + ")", dataTricks.round(this.distInt, 3) + "µm"}, {"Centre of the max intensity", "(" + dataTricks.round(this.xMax, 3) + ", " + dataTricks.round(this.yMax, 3) + ")", dataTricks.round(this.distMax, 3) + "µm"}, {"Centre of the 100% zone", "(" + dataTricks.round(this.xCent100, 3) + ", " + dataTricks.round(this.yCent100, 3) + ")", dataTricks.round(this.dist100, 3) + "µm"}};
    }

    public String[][] getTableForReport() {
        String[][] out = new String[3][10];
        out[0][0] = "Location";

        int i;
        for(i = 1; i < 10; ++i) {
            out[0][i] = ((fieldIlluminationArray)this.remarkInt.elementAt(i - 1)).name;
        }

        out[1][0] = "Intensity";

        for(i = 1; i < 10; ++i) {
            out[1][i] = "" + ((fieldIlluminationArray)this.remarkInt.elementAt(i - 1)).intensity;
        }

        out[2][0] = "Intensity relative to max";

        for(i = 1; i < 10; ++i) {
            out[2][i] = "" + dataTricks.round(((fieldIlluminationArray)this.remarkInt.elementAt(i - 1)).relativeInt, 3);
        }

        return out;
    }

    private double[][] getProfile(ImagePlus img, Line line) {
        double[][] out = new double[2][];
        line.setStrokeWidth(1.0F);
        img.setRoi(line);
        ProfilePlot pp = new ProfilePlot(img);
        out[1] = pp.getProfile();
        double length = img.getRoi().getLength();
        int nPoints = out[1].length;
        out[0] = new double[out[1].length];

        for(int i = 0; i < nPoints; ++i) {
            out[0][i] = (double)i * length / (double)(nPoints - 1) - length / 2.0D;
        }

        img.killRoi();
        return out;
    }

    public void saveData(String path, String filename, int stepWidth, int barWidth) {
        FileSaver fs = new FileSaver(this.getPattern(stepWidth, barWidth));
        fs.saveAsJpeg(path + filename + "_pattern.jpg");
        fs = new FileSaver(this.getProfilesImage());
        fs.saveAsJpeg(path + filename + "_intensityProfiles.jpg");
        fileTricks.save(this.getStringData(), path + filename + "_stats.xls");
        fileTricks.save(this.getStringProfiles(), path + filename + "_intensityProfiles.xls");
    }
}

