//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package uk.ac.warwick.camdu;

import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.Plot;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.measure.CurveFitter;
import ij.plugin.Slicer;
import java.awt.Color;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

class PSFprofiler {
    private static final double SQRT2LN2 = Math.sqrt(2.0D * Math.log(2.0D));
    public static final int X = 0;
    public static final int Y = 1;
    public static final int Z = 2;
    private ImagePlus ip;
    private int[] center;
    private double[][] xProfile;
    private double xR2;
    private String xParamString;
    private double[][] yProfile;
    private double yR2;
    private String yParamString;
    private double[][] zProfile;
    private double zR2;
    private String zParamString;
    private Calibration cal;
    private final double[] resol;

    PSFprofiler(ImagePlus ip) {
        this.xProfile = null;
        double[] xParams = null;
        this.xR2 = 0.0D / 0.0;
        this.xParamString = "Fitted on y = a + (b-a)*exp(-(x-c)^2/(2*d^2))";
        this.yProfile = null;
        double[] yParams = null;
        this.yR2 = 0.0D / 0.0;
        this.yParamString = "Fitted on y = a + (b-a)*exp(-(x-c)^2/(2*d^2))";
        this.zProfile = null;
        double[] zParams = null;
        this.zR2 = 0.0D / 0.0;
        this.zParamString = "Fitted on y = a + (b-a)*exp(-(x-c)^2/(2*d^2))";
        this.cal = new Calibration();
        this.resol = new double[]{0.0D, 0.0D, 0.0D};
        if (ip.getNSlices() == 1) {
            throw new IllegalArgumentException("PSFprofiler requieres a stack");
        } else {
            this.ip = ip;
            this.center = (new findMax()).getAllCoordinates(ip);
            this.cal = ip.getCalibration();
            ip.setSlice(this.center[2]);
            this.getXprofileAndFit();
            this.getYprofileAndFit();
            this.getZprofileAndFit();
        }
    }

    private PSFprofiler(String path) {
        this(new ImagePlus(path));
    }

    private void getXprofileAndFit() {
        this.xProfile = new double[3][this.ip.getWidth()];
        this.xProfile[1] = this.ip.getProcessor().getLine(0.0D, (double)this.center[1], (double)(this.ip.getWidth() - 1), (double)this.center[1]);
        this.fitProfile(this.xProfile, 0);
    }

    private void getYprofileAndFit() {
        this.yProfile = new double[3][this.ip.getHeight()];
        this.yProfile[1] = this.ip.getProcessor().getLine((double)this.center[0], 0.0D, (double)this.center[0], (double)(this.ip.getHeight() - 1));
        this.fitProfile(this.yProfile, 1);
    }

    private void getZprofileAndFit() {
        this.ip.setCalibration(new Calibration());
        this.ip.setRoi(new Line(0, this.center[1], this.ip.getWidth() - 1, this.center[1]));
        ImagePlus crossX = (new Slicer()).reslice(this.ip);
        this.ip.killRoi();
        this.ip.setCalibration(this.cal);
        this.zProfile = new double[3][this.ip.getNSlices()];
        this.zProfile[1] = crossX.getProcessor().getLine((double)this.center[0], 0.0D, (double)this.center[0], (double)(crossX.getHeight() - 1));
        this.fitProfile(this.zProfile, 2);
    }

    private void fitProfile(double[][] profile, int dimension) {
        double max = profile[1][0];
        double pixelSize = 1.0D;
        int resolIndex = 0;
        switch(dimension) {
            case 0:
                pixelSize = this.cal.pixelWidth;
                break;
            case 1:
                pixelSize = this.cal.pixelHeight;
                resolIndex = 1;
                break;
            case 2:
                pixelSize = this.cal.pixelDepth;
                resolIndex = 2;
        }

        double[] params = new double[]{max, max, 0.0D, 2.0D * pixelSize};

        for(int i = 0; i < profile[0].length; ++i) {
            profile[0][i] = (double)i * pixelSize;
            double currVal = profile[1][i];
            params[0] = Math.min(params[0], currVal);
            if (currVal > max) {
                params[1] = currVal;
                params[2] = profile[0][i];
                max = currVal;
            }
        }

        CurveFitter cv = new CurveFitter(profile[0], profile[1]);
        cv.setInitialParameters(params);
        cv.doFit(12);
        params = cv.getParams();
        String paramString = cv.getResultString();
        paramString = paramString.substring(paramString.lastIndexOf("ms") + 2);
        switch(dimension) {
            case 0:
                this.xParamString = this.xParamString + paramString;
                this.xR2 = cv.getFitGoodness();
                break;
            case 1:
                this.yParamString = this.yParamString + paramString;
                this.yR2 = cv.getFitGoodness();
                break;
            case 2:
                this.zParamString = this.zParamString + paramString;
                this.zR2 = cv.getFitGoodness();
        }

        for(int i = 0; i < profile[0].length; ++i) {
            profile[2][i] = CurveFitter.f(12, params, profile[0][i]);
        }

        this.resol[resolIndex] = 2.0D * SQRT2LN2 * params[3];
    }

    @SuppressWarnings("deprecation")
    private Plot getXplot() {
        Plot plot = new Plot("Profile plot along the x axis", "x (" + this.cal.getUnit() + ")", "Intensity (AU)", this.xProfile[0], this.xProfile[2]);
        plot.setSize(300, 200);
        plot.setColor(Color.red);
        plot.addPoints(this.xProfile[0], this.xProfile[1], 0);
        plot.setColor(Color.black);
        plot.addLabel(0.6D, 0.13D, "Dots: measured\nLine: fitted");
        return plot;
    }

    @SuppressWarnings("deprecation")
    private Plot getYplot() {
        Plot plot = new Plot("Profile plot along the y axis", "y (" + this.cal.getUnit() + ")", "Intensity (AU)", this.yProfile[0], this.yProfile[2]);
        plot.setSize(300, 200);
        plot.setColor(Color.red);
        plot.addPoints(this.yProfile[0], this.yProfile[1], 0);
        plot.setColor(Color.black);
        plot.addLabel(0.6D, 0.13D, "Dots: measured\nLine: fitted");
        return plot;
    }

    @SuppressWarnings("deprecation")
    private Plot getZplot() {
        Plot plot = new Plot("Profile plot along the z axis", "z (" + this.cal.getUnit() + ")", "Intensity (AU)", this.zProfile[0], this.zProfile[2]);
        plot.setSize(300, 200);
        plot.setColor(Color.red);
        plot.addPoints(this.zProfile[0], this.zProfile[1], 0);
        plot.setColor(Color.black);
        plot.addLabel(0.6D, 0.13D, "Dots: measured\nLine: fitted");
        return plot;
    }

    double[] getResolutions() {
        return this.resol;
    }

    private String getUnit() {
        return this.cal.getUnit();
    }

    public String getXParams() {
        return this.xParamString;
    }

    public String getYParams() {
        return this.yParamString;
    }

    public String getZParams() {
        return this.zParamString;
    }

    public void saveProfiles(String path, String filename) {
        this.saveProfile(path, filename + "_x-profile", this.xProfile);
        this.saveProfile(path, filename + "_y-profile", this.yProfile);
        this.saveProfile(path, filename + "_z-profile", this.zProfile);
    }

    private void saveProfile(String path, String filename, double[][] data) {
        BufferedWriter out = null;

        try {
            out = new BufferedWriter(new FileWriter(path + filename + ".xls"));
            out.write("Distance (" + this.cal.getUnit() + ")\tRaw_data\tFitted_data\n");

            for(int j = 0; j < data[0].length; ++j) {
                String line = "";

                for(int i = 0; i < 3; ++i) {
                    line = line + data[i][j] + "\t";
                }

                out.write(line);
                out.newLine();
            }

            out.close();
        } catch (IOException var8) {
            Logger.getLogger(PSFprofiler.class.getName()).log(Level.SEVERE, (String)null, var8);
        }

    }

    private String[][] getSummary(microscope microscope) {
        return new String[][]{{"", "x", "y", "z"}, {"FWHM", dataTricks.round(this.getResolutions()[0], 3) + " " + this.getUnit(), dataTricks.round(this.getResolutions()[1], 3) + " " + this.getUnit(), dataTricks.round(this.getResolutions()[2], 3) + " " + this.getUnit()}, {"Theoretical resolution", dataTricks.round(microscope.resolution[0], 3) + " µm", dataTricks.round(microscope.resolution[1], 3) + " µm", dataTricks.round(microscope.resolution[2], 3) + " µm"}, {"Fit goodness", String.valueOf(dataTricks.round(this.xR2, 3)), String.valueOf(dataTricks.round(this.yR2, 3)), String.valueOf(dataTricks.round(this.zR2, 3))}};
    }

    public void saveSummary(String path, String filename, microscope microscope) {
        BufferedWriter out = null;

        try {
            out = new BufferedWriter(new FileWriter(path + filename + "_summary.xls"));
            String[][] array = this.getSummary(microscope);

            for(int j = 0; j < array[0].length; ++j) {
                String line = "";

                for (String[] strings : array) {
                    line = line + strings[j].replaceAll("\n", " ") + "\t";
                }

                out.write(line);
                out.newLine();
            }

            out.close();
        } catch (IOException var9) {
            Logger.getLogger(PSFprofiler.class.getName()).log(Level.SEVERE, (String)null, var9);
        }

    }

    public void savePlots(String path, String filename) {
        FileSaver fs = new FileSaver(this.getXplot().getImagePlus());
        fs.saveAsJpeg(path + filename + "_x-plot.jpg");
        fs = new FileSaver(this.getYplot().getImagePlus());
        fs.saveAsJpeg(path + filename + "_y-plot.jpg");
        fs = new FileSaver(this.getZplot().getImagePlus());
        fs.saveAsJpeg(path + filename + "_z-plot.jpg");
    }
}
