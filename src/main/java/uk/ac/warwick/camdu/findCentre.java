package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import ij.ImagePlus;
import ij.gui.PolygonRoi;
import ij.gui.Wand;
import ij.process.EllipseFitter;
import ij.process.ImageConverter;
import ij.process.ImageStatistics;
import utilities.segmentation.HistogramSegmentation;

public class findCentre {
    public static final int XY = 0;
    public static final int XZ = 1;
    public static final int YZ = 2;

    public findCentre() {
    }

    public double[] getAllCoordinates(ImagePlus ip) {
        double[] coord;
        if (ip.getNSlices() == 1) {
            coord = this.get2DCenter(ip, 0);
        } else {
            double[] coord2D = this.get2DCenter(ip, 0);
            coord = new double[]{coord2D[0], coord2D[1], this.get2DCenter(ip, 1)[1]};
        }

        return coord;
    }

    public double[] get2DCenter(ImagePlus ip, int profileType) {
        double[] coord = new double[2];
        ImagePlus proj = null;
        switch(profileType) {
            case 0:
                proj = (new sideViewGenerator()).getXYview(ip, 3);
                break;
            case 1:
                proj = (new sideViewGenerator()).getXZview(ip, 3, false);
                break;
            case 2:
                proj = (new sideViewGenerator()).getYZview(ip, 3, false);
                break;
            default:
                proj = (new sideViewGenerator()).getXYview(ip, 3);
        }

        (new ImageConverter(proj)).convertToGray8();
        proj.updateImage();
        HistogramSegmentation hs = new HistogramSegmentation(proj);
        hs.calcLimits(2, 100, 0, true);
        proj = hs.getsegmentedImage(proj, 1);
        proj.updateImage();
        ImageStatistics is = proj.getStatistics(64);
        Wand wand = new Wand(proj.getProcessor());
        coord[0] = is.xCenterOfMass;
        coord[1] = is.yCenterOfMass;
        EllipseFitter ef = new EllipseFitter();

        do {
            wand.autoOutline((int)(coord[0] + 0.5D), (int)(coord[1] + 0.5D), 128, 255);
            proj.setRoi(new PolygonRoi(wand.xpoints, wand.ypoints, wand.npoints, 2));
            ef.fit(proj.getProcessor(), (ImageStatistics)null);
            coord[0] = ef.xCenter + 1.0D;
            coord[1] = ef.yCenter;
        } while(ef.minor < 2.0D);

        int var10002 = (int)coord[0]--;
        return coord;
    }
}

