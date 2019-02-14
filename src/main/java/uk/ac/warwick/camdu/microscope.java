package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import ij.measure.Calibration;
import java.text.DateFormat;
import java.util.Calendar;

public class microscope {
    public static final String[] MICRO = new String[]{"WideField", "Confocal"};
    public static final int WIDEFIELD = 0;
    public static final int CONFOCAL = 1;
    public int microscope = 0;
    public double wavelength = 0.0D;
    public double NA = 0.0D;
    public double pinhole = 0.0D;
    public Calibration cal = null;
    public double[] resolution;
    public String reportHeader = "";
    public String date = "";
    public String sampleInfos = "";
    public String comments = "";

    public microscope(Calibration cal, int microscope, double wavelength, double NA, double pinhole, String sampleInfos, String comments) {
        this.cal = cal;
        this.microscope = microscope;
        this.wavelength = wavelength;
        this.NA = NA;
        this.pinhole = pinhole;
        this.sampleInfos = sampleInfos;
        this.comments = comments;
        this.resolution = (new resolutionCalculator(microscope, wavelength, NA)).getResolutions();
        this.reportHeader = "Microscope: " + MICRO[microscope] + "\nWavelength: " + wavelength + " nm\nNA: " + NA + "\nSampling rate: " + this.round(cal.pixelWidth, 3) + "x" + this.round(cal.pixelHeight, 3) + "x" + this.round(cal.pixelDepth, 3) + " " + cal.getUnit();
        if (microscope == 1) {
            this.reportHeader = this.reportHeader + "\nPinhole: " + pinhole + " Airy Units";
        }

        DateFormat df = DateFormat.getDateTimeInstance(1, 3);
        this.date = df.format(Calendar.getInstance().getTime()).toString();
    }

    private double round(double nb2round, int nbOfDigits) {
        return (double)Math.round(nb2round * Math.pow(10.0D, (double)nbOfDigits)) / Math.pow(10.0D, (double)nbOfDigits);
    }
}

