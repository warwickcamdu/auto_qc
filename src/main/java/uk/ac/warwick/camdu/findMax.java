package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import ij.ImagePlus;

class findMax {
    findMax() {
    }

    int[] getAllCoordinates(ImagePlus ip) {
        int[] coord;
        if (ip.getNSlices() == 1) {
            coord = this.get2DCenter(ip);
        } else {
            int[] coord2D = this.get2DCenter((new sideViewGenerator()).getXYview(ip, 1));
            coord = new int[]{coord2D[0], coord2D[1], 0};
            coord[2] = this.getZmax(ip, coord[0], coord[1]);
        }

        return coord;
    }

    private int[] get2DCenter(ImagePlus ip) {
        int max = 0;
        int[] coord = new int[2];

        for(int y = 0; y < ip.getHeight(); ++y) {
            for(int x = 0; x < ip.getWidth(); ++x) {
                int currVal = ip.getProcessor().getPixel(x, y);
                if (currVal > max) {
                    coord[0] = x;
                    coord[1] = y;
                    max = currVal;
                }
            }
        }

        return coord;
    }

    public int getXmax(ImagePlus ip, int yPos) {
        int max = 0;
        int coord = 0;

        for(int x = 0; x < ip.getWidth(); ++x) {
            int currVal = ip.getProcessor().getPixel(x, yPos);
            if (currVal > max) {
                coord = x;
                max = currVal;
            }
        }

        return coord;
    }

    public int getYmax(ImagePlus ip, int xPos) {
        int max = 0;
        int coord = 0;

        for(int y = 0; y < ip.getHeight(); ++y) {
            int currVal = ip.getProcessor().getPixel(xPos, y);
            if (currVal > max) {
                coord = y;
                max = currVal;
            }
        }

        return coord;
    }

    private int getZmax(ImagePlus ip, int xPos, int yPos) {
        int max = 0;
        int coord = 1;

        for(int z = 1; z <= ip.getNSlices(); ++z) {
            ip.setSlice(z);
            int currVal = ip.getProcessor().getPixel(xPos, yPos);
            if (currVal > max) {
                coord = z;
                max = currVal;
            }
        }

        return coord;
    }
}

