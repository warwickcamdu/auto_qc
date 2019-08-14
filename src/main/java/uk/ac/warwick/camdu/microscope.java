package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import ij.measure.Calibration;
import java.text.DateFormat;
import java.util.Calendar;

public class microscope {
    static final String[] MICRO = new String[]{"WideField", "Confocal"};
    static final int WIDEFIELD = 0;
    public static final int CONFOCAL = 1;
    public int microscope = 0;
    double wavelength = 0.0D;
    double NA = 0.0D;
    double pinhole = 0.0D;
    public final double[] resolution;

    public microscope(Calibration cal, int microscope, double wavelength, double NA, double pinhole, String sampleInfos, String comments) {
        Calibration cal1 = cal;
        this.microscope = microscope;
        this.wavelength = wavelength;
        this.NA = NA;
        this.pinhole = pinhole;
        String sampleInfos1 = sampleInfos;
        String comments1 = comments;
        this.resolution = (new resolutionCalculator(microscope, wavelength, NA)).getResolutions();
        String reportHeader = "Microscope: " + MICRO[microscope] + "\nWavelength: " + wavelength + " nm\nNA: " + NA + "\nSampling rate: " + this.round(cal.pixelWidth, 3) + "x" + this.round(cal.pixelHeight, 3) + "x" + this.round(cal.pixelDepth, 3) + " " + cal.getUnit();
        if (microscope == 1) {
            reportHeader = reportHeader + "\nPinhole: " + pinhole + " Airy Units";
        }

        DateFormat df = DateFormat.getDateTimeInstance(1, 3);
        String date = df.format(Calendar.getInstance().getTime()).toString();
    }

    @SuppressWarnings("SameParameterValue")
    private double round(double nb2round, int nbOfDigits) {
        return (double)Math.round(nb2round * Math.pow(10.0D, (double)nbOfDigits)) / Math.pow(10.0D, (double)nbOfDigits);
    }
}

