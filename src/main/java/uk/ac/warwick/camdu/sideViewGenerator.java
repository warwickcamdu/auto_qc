package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import ij.ImagePlus;
import ij.gui.Line;
import ij.gui.NewImage;
import ij.measure.Calibration;
import ij.plugin.Slicer;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.awt.Font;

class sideViewGenerator {
    public static final int XZ_VIEW = 0;
    public static final int YZ_VIEW = 1;
    public static final int AVG_METHOD = 0;
    public static final int MAX_METHOD = 1;
    public static final int MIN_METHOD = 2;
    public static final int SUM_METHOD = 3;
    public static final int SD_METHOD = 4;
    public static final int MEDIAN_METHOD = 5;

    public sideViewGenerator() {
    }

    public ImagePlus getXYview(ImagePlus ip, int projType) {
        Calibration cal = ip.getCalibration();
        ip.setCalibration(new Calibration());
        ZProjector zp = new ZProjector(ip);
        zp.setMethod(projType);
        zp.doProjection();
        ImagePlus output = zp.getProjection();
        ip.setCalibration(cal);
        output.setCalibration(cal);
        return output;
    }

    public ImagePlus getXZview(ImagePlus ip, int projType, boolean keepCalibration) {
        return this.sideView(ip, projType, keepCalibration, 0);
    }

    public ImagePlus getYZview(ImagePlus ip, int projType, boolean keepCalibration) {
        return this.sideView(ip, projType, keepCalibration, 1);
    }

    public ImagePlus getPanelView(ImagePlus ip, int projType, boolean keepCalibration, boolean addScaleBar, int size, boolean addCross, double[] coordCross, int crossRadius) {
        Font font = new Font("Times New Roman", 1, 12);
        Calibration cal = ip.getCalibration();
        double xzRatio = cal.pixelDepth / cal.pixelWidth;
        double yzRatio = cal.pixelDepth / cal.pixelHeight;
        ImageProcessor xy = this.getXYview(ip, projType).getProcessor();
        if (addCross) {
            int[] coord = new int[]{(int)(coordCross[0] + 0.5D), (int)(coordCross[1] + 0.5D)};
            this.addCross(xy, coord, crossRadius);
        }

        xy.setColor(Color.white);
        xy.setFont(font);
        xy.drawString("XY", 3, 15);
        if (addScaleBar) {
            this.addScaleBar(xy, cal, size);
        }

        ImageProcessor xz = this.getXZview(ip, projType, keepCalibration).getProcessor();
        if (addCross) {
            int[] coord = new int[]{(int)(coordCross[0] + 0.5D), keepCalibration ? (int)(xzRatio * (coordCross[2] + 0.5D)) : (int)(coordCross[2] + 0.5D)};
            this.addCross(xz, coord, crossRadius);
        }

        xz.setColor(Color.white);
        xz.setFont(font);
        xz.drawString("XZ", 3, 15);
        ImageProcessor yz = this.getYZview(ip, projType, keepCalibration).getProcessor().rotateRight();
        yz.flipHorizontal();
        if (addCross) {
            int[] coord = new int[]{keepCalibration ? (int)(yzRatio * (coordCross[2] + 0.5D)) : (int)(coordCross[2] + 0.5D), (int)(coordCross[1] + 0.5D)};
            this.addCross(yz, coord, crossRadius);
        }

        yz.setColor(Color.white);
        yz.setFont(font);
        yz.drawString("YZ", 3, 15);
        ImageProcessor iproc = xy.createProcessor(xy.getWidth() + 10 + yz.getWidth(), xy.getHeight() + 10 + xz.getHeight());
        iproc.setColorModel(iproc.getDefaultColorModel());
        iproc.setColor(Color.white);
        iproc.fill();
        iproc.insert(xy, 0, 0);
        iproc.insert(yz, xy.getWidth() + 10, 0);
        iproc.insert(xz, 0, xy.getHeight() + 10);
        return new ImagePlus("Panel view", iproc);
    }

    private ImagePlus sideView(ImagePlus ip, int projType, boolean keepCalibration, int view) {
        Calibration cal = ip.getCalibration();
        ip.setCalibration(new Calibration());
        ImagePlus reslicedStack = null;
        if (view == 0) {
            reslicedStack = (new Slicer()).reslice(ip);
        } else {
            for(int i = 0; i < ip.getWidth(); ++i) {
                Line line = new Line(i, 0, i, ip.getHeight() - 1);
                ip.setRoi(line);
                ImagePlus slice = (new Slicer()).reslice(ip);
                if (i == 0) {
                    reslicedStack = NewImage.createImage("YZ view", slice.getWidth(), slice.getHeight(), ip.getWidth(), slice.getBitDepth(), 1);
                }

                reslicedStack.setSlice(i + 1);
                reslicedStack.setProcessor("YZ view", slice.getProcessor());
            }

            ip.killRoi();
        }

        ip.setCalibration(cal);
        ZProjector zp = new ZProjector(reslicedStack);
        zp.setMethod(projType);
        zp.doProjection();
        ImagePlus output = zp.getProjection();
        if (keepCalibration) {
            ImageProcessor iproc = output.getProcessor();
            iproc.setInterpolate(true);
            if (view == 0) {
                iproc = iproc.resize(output.getWidth(), (int)((double)output.getHeight() * cal.pixelDepth / cal.pixelWidth));
            } else {
                iproc = iproc.resize(output.getWidth(), (int)((double)output.getHeight() * cal.pixelDepth / cal.pixelHeight));
            }

            output = new ImagePlus("sideView", iproc);
        } else {
            if (view == 0) {
                cal.pixelHeight = cal.pixelDepth;
                cal.pixelDepth = 1.0D;
            } else {
                cal.pixelWidth = cal.pixelHeight;
                cal.pixelHeight = cal.pixelDepth;
                cal.pixelDepth = 1.0D;
            }

            output.setCalibration(cal);
        }

        return output;
    }

    private void addScaleBar(ImageProcessor ip, Calibration cal, int barWidth) {
        int fraction = 20;
        int barWidthInPixels = (int)((double)barWidth / cal.pixelWidth);
        int barHeightInPixels = 4;
        String barString = barWidth + " " + cal.getUnits();
        int stringWidth = ip.getStringWidth(barString);
        int fontSize = 12;
        int width = ip.getWidth();
        int height = ip.getHeight();
        int x = width - width / fraction - barWidthInPixels;
        int y = height - height / fraction - barHeightInPixels - fontSize;
        int xOffset = (barWidthInPixels - stringWidth) / 2;
        int yOffset = barHeightInPixels + fontSize + fontSize / 4;
        ip.setColor(Color.white);
        ip.setRoi(x, y, barWidthInPixels, barHeightInPixels);
        ip.fill();
        ip.drawString(barString, x + xOffset, y + yOffset);
    }

    private void addCross(ImageProcessor ip, int[] coord, int radius) {
        ip.setColor(Color.white);
        ip.setLineWidth(Math.max(2, Math.max(ip.getWidth(), ip.getHeight()) / 500));
        ip.multiply(0.5D);
        ip.drawLine(coord[0], coord[1] - radius, coord[0], coord[1] + radius);
        ip.drawLine(coord[0] - radius, coord[1], coord[0] + radius, coord[1]);
    }
}
