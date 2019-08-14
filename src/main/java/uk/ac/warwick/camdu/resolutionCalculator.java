package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


class resolutionCalculator {
    public final int WIDEFIELD = 0;
    public final int CONFOCAL = 1;
    private final double[] resolutions = new double[]{0.0D, 0.0D, 0.0D};

    resolutionCalculator(int microscope, double wavelength, double NA) {
        wavelength /= 1000.0D;
        switch(microscope) {
            case 0:
                this.resolutions[0] = 0.61D * wavelength / NA;
                this.resolutions[1] = this.resolutions[0];
                this.resolutions[2] = 2.0D * wavelength / Math.pow(NA, 2.0D);
                break;
            case 1:
                this.resolutions[0] = 0.4D * wavelength / NA;
                this.resolutions[1] = this.resolutions[0];
                this.resolutions[2] = 1.4D * wavelength / Math.pow(NA, 2.0D);
        }

    }

    double[] getResolutions() {
        return this.resolutions;
    }
}
