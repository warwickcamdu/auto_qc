package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import ij.measure.Calibration;

class dataTricks {
    public static final int MIN = 0;
    public static final int MAX = 1;

    public dataTricks() {
    }

    public static double round(double nb2round, int nbOfDigits) {
        return (double)Math.round(nb2round * Math.pow(10.0D, (double)nbOfDigits)) / Math.pow(10.0D, (double)nbOfDigits);
    }

    public static double dist(double[] coord1, double[] coord2, Calibration cal) {
        double calX = cal.pixelWidth;
        double calY = cal.pixelHeight;
        double calZ = cal.pixelDepth;
        return coord1.length == 2 ? Math.sqrt((coord2[0] - coord1[0]) * (coord2[0] - coord1[0]) * calX * calX + (coord2[1] - coord1[1]) * (coord2[1] - coord1[1]) * calY * calY) : Math.sqrt((coord2[0] - coord1[0]) * (coord2[0] - coord1[0]) * calX * calX + (coord2[1] - coord1[1]) * (coord2[1] - coord1[1]) * calY * calY + (coord2[2] - coord1[2]) * (coord2[2] - coord1[2]) * calZ * calZ);
    }

    public static double min(double[] input) {
        return extremum(0, (double[])input);
    }

    public static int min(int[] input) {
        return extremum(0, (int[])input);
    }

    public static double max(double[] input) {
        return extremum(1, (double[])input);
    }

    public static int max(int[] input) {
        return extremum(1, (int[])input);
    }

    public static double[] transTypeInt2Double(int[] array) {
        double[] out = new double[array.length];

        for(int i = 0; i < array.length; ++i) {
            out[i] = (double)array[i];
        }

        return out;
    }

    private static double extremum(int type, double[] input) {
        double out = input[0];

        for(int i = 1; i < input.length; ++i) {
            switch(type) {
                case 0:
                    out = Math.min(out, input[i]);
                    break;
                case 1:
                    out = Math.max(out, input[i]);
            }
        }

        return out;
    }

    private static int extremum(int type, int[] input) {
        int out = input[0];

        for(int i = 1; i < input.length; ++i) {
            switch(type) {
                case 0:
                    out = Math.min(out, input[i]);
                    break;
                case 1:
                    out = Math.max(out, input[i]);
            }
        }

        return out;
    }

    public static int findFirstNonZero(int[] input) {
        int out;
        for(out = 0; input[out] == 0 && out < input.length; ++out) {
        }

        return out;
    }

    public static int findLastNonZero(int[] input) {
        int out;
        for(out = input.length - 1; input[out] == 0 && out >= 0; --out) {
        }

        return out;
    }
}

