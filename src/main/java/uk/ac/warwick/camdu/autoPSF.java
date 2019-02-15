/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */

package uk.ac.warwick.camdu;

import ij.IJ;
import ij.ImagePlus;

import java.awt.event.ActionEvent;
import java.io.FileWriter;
import java.io.IOException;

import ij.WindowManager;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.process.FloatPolygon;
import ij.process.ImageProcessor;
import loci.formats.FormatException;
import loci.plugins.BF;

import net.imagej.ImageJ;
import net.imagej.ops.Ops;
import net.imagej.ops.special.computer.Computers;
import net.imagej.ops.special.computer.UnaryComputerOp;
import net.imglib2.*;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;

import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Objects;


/**
 */

@Plugin(type = Command.class, menuPath = "Plugins>autoQC>autoFOV")
public class autoPSF<T extends RealType<T>> extends Component implements Command {

    @Parameter
    private ImageJ ij;



    String ext = ".dv";
    int beads = 3;
    double corr_factor_x = 1.186;
    double corr_factor_y = 1.186;
    double corr_factor_z = 1.186;
    int minSeparation = 15;
    Calibration calibration;

    String srcDir = "";

    public void setExtension(String extension){
        ext = extension;

    }

    public void setBeads(int beadnum){
        beads = beadnum;

    }

    public void setCorrX(double corr_x){
        corr_factor_x = corr_x;

    }

    public void setCorrY(double corr_y){
        corr_factor_y = corr_y;

    }

    public void setCorrZ(double corr_z){
        corr_factor_z = corr_z;

    }

    public void setMinSep(int minsep){
        minSeparation = minsep;

    }

    public void setDir(String sourceDir){
        srcDir = sourceDir;

    }


    public void createUI(){
        JTextField extField = new JTextField(".dv",10);
        JTextField beadField = new JTextField("5",5);
        JTextField corrXField = new JTextField("1.168",5);
        JTextField corrYField = new JTextField("1.168",5);
        JTextField corrZField = new JTextField("1.168",5);
        JTextField sepField = new JTextField("15",5);

        JButton browseBtn = new JButton("Browse:");

        browseBtn.addActionListener(e -> browseButtonActionPerformed(e));



        System.out.println(srcDir);



        JPanel myPanel = new JPanel();

        myPanel.add(new JLabel("File extension:"));
        myPanel.add(extField);

        myPanel.add(new JLabel("Number of beads:"));
        myPanel.add(beadField);

        myPanel.add(new JLabel("Correction factor (x):"));
        myPanel.add(corrXField);

        myPanel.add(new JLabel("Correction factor (y):"));
        myPanel.add(corrYField);

        myPanel.add(new JLabel("Correction factor (z):"));
        myPanel.add(corrZField);

        myPanel.add(new JLabel("Minimum bead separation (px):"));
        myPanel.add(sepField);

        myPanel.add(new JLabel("Please select your datset:"));
        myPanel.add(browseBtn);

        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

        int result = JOptionPane.showConfirmDialog(
                null, myPanel, "autoColoc", JOptionPane.OK_CANCEL_OPTION);

        setExtension(extField.getText());
        setBeads(Integer.parseInt(beadField.getText()));
        setCorrX(Double.parseDouble(corrXField.getText()));
        setCorrY(Double.parseDouble(corrYField.getText()));

        setMinSep(Integer.parseInt(sepField.getText()));



    }

    private void browseButtonActionPerformed(ActionEvent e) {

        JFileChooser chooser = new JFileChooser();
        chooser.setCurrentDirectory(new java.io.File("."));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);
        //chooser.showOpenDialog(this);
        String sourceDir = "";

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedDir = chooser.getSelectedFile();
            sourceDir = selectedDir.getAbsolutePath();

        }
        else {
            System.out.println("No Selection ");
        }


        setDir(sourceDir);
    }

    public void run() {


        createUI();

        ij = new net.imagej.ImageJ();
        //String srcDir = selectedDir.getAbsolutePath();

        System.out.println(srcDir);

        ArrayList<String> folders = new ArrayList<String>();
        ArrayList<String> filenames = new ArrayList<String>();

        File selectedDir = new File(srcDir);

        Img<FloatType> currentFile;

        for (final File fileEntry : Objects.requireNonNull(selectedDir.listFiles())){

            if (fileEntry.getName().endsWith(ext)&&fileEntry.getName().contains("psf")){

                System.out.println("Processing file: " + fileEntry.getName());
                String path = selectedDir + File.separator + fileEntry.getName();

                currentFile = readFile(path, selectedDir);

                double[][] finalResult = processing(currentFile);

                String resultPath = selectedDir + File.separator + "summary_PSF.csv";

                WriteFile(resultPath,finalResult);

            }




        }


        // skip irrelevant filenames, do stuff for relevant ones


    }

    private Img<FloatType> readFile(String arg, File selectDir) {

        //  OpenDialog od = new OpenDialog("Open Image File...", arg);
        //  String dir = od.getDirectory();
        //  String name = od.getFileName();
        //  String id = dir + name;
        long[] dimensions = new long[]{
                512, 512
        };

        Img<FloatType> imgFinal = ArrayImgs.floats(dimensions);

        ImagePlus[] imps = new ImagePlus[0];

        ImagePlus imp = new ImagePlus();
        try {

            imps = BF.openImagePlus(arg);
            imp = imps[0];
            calibration = imp.getCalibration();

            imgFinal = ImageJFunctions.convertFloat(imps[0]);
            // for (ImagePlus imp : imps) imp.show();
            // We don't need to show them

        } catch (FormatException | IOException exc) {

            IJ.error("Sorry, an error occurred: " + exc.getMessage());

        }

        return imgFinal;




    }

    public double[][] processing(Img image){
    //private void processing(Img<FloatType> image){


        IJ.run("Set Measurements...", "min centroid integrated redirect=None decimal=3");
        System.out.println("Opened file, processing");

        //ImageJFunctions.show(image);
        // Crops the image to get middle of the field of view

        FinalInterval interval = FinalInterval.createMinSize(0,0,0,image.dimension(0),image.dimension(1),image.dimension(2));
        if (image.dimension(0) > 300 && image.dimension(1) > 300){
            System.out.println(image.dimension(0));
            interval = FinalInterval.createMinSize(image.dimension(0)/2-150,image.dimension(1)/2-150,0,300,300,image.dimension(2));
        }


        RandomAccessibleInterval cropped;
        cropped  = ij.op().transform().crop(image,interval, true);

//        ImageJFunctions.show(cropped);
        int[] projected_dimensions = new int[cropped.numDimensions() - 1];

        int dim = 2;
        int d;
        for (d = 0; d < cropped.numDimensions(); ++d) {
            if (d != dim) projected_dimensions[d] = (int) cropped.dimension(d);
        }

        Img<FloatType> proj = ij.op().create().img(
                new FinalDimensions(projected_dimensions), new FloatType());

        UnaryComputerOp maxOp = Computers.unary(ij.op(),Ops.Stats.Max.class,RealType.class, Iterable.class);

        Img<T> projection = (Img<T>) ij.op().transform().project(proj, cropped, maxOp, 2);
        ImageJFunctions.show(proj);




        // detect beads and measure for intensity and x/y coords
        IJ.run("Find Maxima...", "noise=20 output=[Point Selection] exclude");
        ImagePlus imp = IJ.getImage();
        // Gets coordinates of ROIs

        Roi test = imp.getRoi();
        FloatPolygon floatPolygon = test.getFloatPolygon();

        float[][] resultsTable = new float[floatPolygon.npoints][3];

        // loops over ROIs and get pixel Vvlue at their coordinate.
        for (int i=1; i < floatPolygon.npoints; i++){

            float intx = floatPolygon.xpoints[i];
            float inty = floatPolygon.ypoints[i];
            final RandomAccess<FloatType> r = proj.randomAccess();
            r.setPosition((int) intx,0);
            r.setPosition((int) inty,1);
            FloatType pixel = r.get();

            resultsTable[i-1][0] = intx;
            resultsTable[i-1][1] = inty;
            resultsTable[i-1][2] = pixel.get();

        }



        // Sorts the Pixel coordinates by the intensity value.
        java.util.Arrays.sort(resultsTable, new java.util.Comparator<float[]>() {
                    public int compare(float[] a, float[] b) {

                        return Double.compare(a[2], b[2]);

                    }
                });

        int countSpots = 0;
        int firstPosition = 1;
        double goodX[] = new double[beads];
        double goodY[] = new double[beads];


        // selects the selected number of pixels based on the specified criteria
        while (countSpots < beads && firstPosition < resultsTable.length ){

            float x1 = resultsTable[firstPosition][0];

            float y1 = resultsTable[firstPosition][1];

            int nextPosition = firstPosition + 1;
            boolean valid = true;

            while (valid && nextPosition < resultsTable.length){

                float x2 = resultsTable[nextPosition][0];
                float y2 = resultsTable[nextPosition][1];

                double dist_sq = Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2);

                if (x2 != x1 && y2 != y1 && dist_sq < Math.pow(minSeparation,2)){

                    valid = false;

                }

                nextPosition++;


            }

            if (valid){
                goodX[countSpots] = resultsTable[firstPosition][0];
                goodY[countSpots] = resultsTable[firstPosition][1];
                countSpots++;

            }

            firstPosition++;

        }



        double[][] finalResults  = new double[beads][3];
        //ij.ui().showUI();
        // loops over selected pixels and crops out the PSFs
        for (int i = 0; i < goodX.length; i++){

            interval = FinalInterval.createMinSize(0,0,0,20,20,cropped.dimension(2));

            if (goodX[i]>10 && goodY[i]>10){
                interval = FinalInterval.createMinSize((int)goodX[i]-10,(int)goodY[i]-10,0,20,20,cropped.dimension(2));
            }


            RandomAccessibleInterval newcropped  = ij.op().transform().crop(cropped,interval, true);
            ImagePlus IPcropped = ImageJFunctions.wrapFloat(newcropped, "test");
            IPcropped.setDimensions(1, (int) newcropped.dimension(2), 1);
            IPcropped.setOpenAsHyperStack(true);
            ImageProcessor ip = IPcropped.getProcessor();
            ip.resetMinAndMax();

            IPcropped.setCalibration(calibration);



            // crops stack around the specified coordinates

            // calls GetRes to extract the resolution form the PSFs
            double[] qcMetrics = GetRes(IPcropped);


            // multiply by the correction factor
            double xRes = qcMetrics[0] * corr_factor_x;
            double yRes = qcMetrics[1] * corr_factor_y;
            double zRes = qcMetrics[2] * corr_factor_z;

            finalResults[i][0] = xRes;
            finalResults[i][1] = yRes;
            finalResults[i][2] = zRes;


        }

        WindowManager.closeAllWindows();
        return finalResults;

    }


    public static boolean WriteFile(String FilePath, double[][] BeatResArray){

            String COMMA_DELIMITER = ",";
            String NEW_LINE_SEPARATOR = "\n";
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(FilePath);
                //Write the CSV file header
                fileWriter.append("x_resolution").append(COMMA_DELIMITER).append("y_resolution").append(COMMA_DELIMITER).append("z_resolution");
                //Add a new line separator after the header
                fileWriter.append(NEW_LINE_SEPARATOR);
                int Datalength = BeatResArray.length;
                for (int x = 0; x <Datalength; x++)
                {

                    fileWriter.append(String.valueOf(BeatResArray[x][0]));
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(String.valueOf(BeatResArray[x][1]));
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(String.valueOf(BeatResArray[x][2]));
                    fileWriter.append(NEW_LINE_SEPARATOR);

                }
            } catch (Exception e) {

                System.out.println("Error in CsvFileWriter !!!");
                e.printStackTrace();

            } finally {

                try {

                    assert fileWriter != null;
                    fileWriter.flush();
                    fileWriter.close();

                } catch (IOException e) {

                    System.out.println("Error while flushing/closing fileWriter !!!");
                    e.printStackTrace();

                }
            }
            return true;

    }

    public static double[] GetRes(ImagePlus BeatStack){

        PSFprofiler profiler=new PSFprofiler(BeatStack);
        return profiler.getResolutions();

    }


    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     * @throws Exception
     */
    public static void main(final String... args) throws Exception {
        // create the ImageJ application context with all available services
        //final ImageJ ij = new ImageJ();
        //ij.ui().showUI();

        // ask the user for a file to open
        //final File file = ij.ui().chooseFile(null, "open");

        //if (file != null) {
            // load the dataset
           // final Dataset dataset = ij.scifio().datasetIO().open(file.getPath());

            // show the image
           // ij.ui().show(dataset);

            // invoke the plugin
           // ij.command().run(autoColoc.class, true);
        autoPSF main_class = new autoPSF();
        main_class.run();
        }
    }


