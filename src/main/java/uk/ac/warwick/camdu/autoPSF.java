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
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.filter.MaximumFinder;
import ij.process.ImageProcessor;
import loci.formats.FormatException;
import loci.plugins.BF;
import net.imagej.ImageJ;
import net.imagej.ops.Ops;
import net.imagej.ops.special.computer.Computers;
import net.imagej.ops.special.computer.UnaryComputerOp;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
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
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;
import java.util.Objects;

import static ij.WindowManager.*;
import static java.lang.Math.ceil;
import static java.lang.Math.round;


/**
 *
 * @param <T>
 */

@Plugin(type = Command.class, menuPath = "Plugins>autoQC>autoPSF")
public class autoPSF<T extends RealType<T>> extends Component implements Command {




    @Parameter
    private ImageJ ij;


    @Parameter(label = "File extension:")
    private String ext = ".tif";

    @Parameter(label = "number of beads:")
    private int beads = 3;

    @Parameter(label = "Correction factor (x):")
    private double corr_factor_x = 1.186;

    @Parameter(label = "Correction factor (y):")
    private double corr_factor_y = 1.186;

    @Parameter(label = "Correction factor (z):")
    private double corr_factor_z = 1.186;

    @Parameter(label = "Bead size (um):")
    private double beadSize = 1.0;

    @Parameter(label = "Noise threshold:")
    private double noiseTol = 100;

    @Parameter(label = "Minimum bead separation (px):")
    private int minSeparation = 15;

    private Calibration calibration;

    @Parameter(style="files", label = "select files:")
    private File[] srcDir;


    private void setExtension(String extension){
        ext = extension;

    }

    private void setBeads(int beadnum){
        beads = beadnum;

    }

    private void setBeadSize(double bsize){
        beadSize = bsize;

    }

    private void setCorrX(double corr_x){
        corr_factor_x = corr_x;

    }

    private void setCorrY(double corr_y){
        corr_factor_y = corr_y;

    }

    private void setCorrZ(double corr_z){
        corr_factor_z = corr_z;

    }

    private void setMinSep(int minsep){
        minSeparation = minsep;

    }

    private void setNoiseTol(double ntol){
        noiseTol = ntol;

    }

    /*private void setDir(String sourceDir){
        srcDir = new File(sourceDir);

    }*/


    private void createUI(){
        JTextField extField = new JTextField(".tif",10);
        JTextField beadField = new JTextField("5",5);
        JTextField beadSizeField = new JTextField("1",5);
        JTextField corrXField = new JTextField("1.168",5);
        JTextField corrYField = new JTextField("1.168",5);
        JTextField corrZField = new JTextField("1.168",5);
        JTextField sepField = new JTextField("15",5);
        JTextField noiseTolField = new JTextField("100",5);

        JButton browseBtn = new JButton("Browse:");

        browseBtn.addActionListener(e -> browseButtonActionPerformed());



        System.out.println(srcDir);



        JPanel myPanel = new JPanel();

        myPanel.add(new JLabel("File extension:"));
        myPanel.add(extField);

        myPanel.add(new JLabel("Number of beads:"));
        myPanel.add(beadField);

        myPanel.add(new JLabel("Bead size (um):"));
        myPanel.add(beadSizeField);

        myPanel.add(new JLabel("Correction factor (x):"));
        myPanel.add(corrXField);

        myPanel.add(new JLabel("Correction factor (y):"));
        myPanel.add(corrYField);

        myPanel.add(new JLabel("Correction factor (z):"));
        myPanel.add(corrZField);

        myPanel.add(new JLabel("Minimum bead separation (px):"));
        myPanel.add(sepField);

        myPanel.add(new JLabel("Noise threshold:"));
        myPanel.add(noiseTolField);

        myPanel.add(new JLabel("Please select your dataset:"));
        myPanel.add(browseBtn);

        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

        JOptionPane.showConfirmDialog(
                null, myPanel, "autoColoc", JOptionPane.OK_CANCEL_OPTION);

        setExtension(extField.getText());
        setBeads(Integer.parseInt(beadField.getText()));
        setCorrX(Double.parseDouble(corrXField.getText()));
        setCorrY(Double.parseDouble(corrYField.getText()));
        setCorrZ(Double.parseDouble(corrZField.getText()));
        setBeadSize(Double.parseDouble(beadSizeField.getText()));
        setMinSep(Integer.parseInt(sepField.getText()));
        setNoiseTol(Double.parseDouble(noiseTolField.getText()));




    }

    private void browseButtonActionPerformed() {

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


        //setDir(sourceDir);
    }


    @Override
    public void run() {


//        createUI();

        ij = new net.imagej.ImageJ();
        //String srcDir = selectedDir.getAbsolutePath();

        //System.out.println(srcDir);



        //File[] selectedDir =srcDir;

        Img<FloatType> currentFile;

        for (final File fileEntry : Objects.requireNonNull(srcDir)){

            if (fileEntry.getName().endsWith(ext)&&fileEntry.getName().contains("psf")){

                System.out.println("Processing file: " + fileEntry.getName());
                String path = fileEntry.getPath();

                currentFile = readFile(path);

                double[][] finalResult = processing(currentFile,path);

                String resultPath = fileEntry.getParent() + File.separator + "summary_PSF.csv";

                WriteFile(resultPath,fileEntry.getName(),finalResult);

            }




        }


        // skip irrelevant filenames, do stuff for relevant ones


    }

    private Img<FloatType> readFile(String arg) {

        //  OpenDialog od = new OpenDialog("Open Image File...", arg);
        //  String dir = od.getDirectory();
        //  String name = od.getFileName();
        //  String id = dir + name;
        long[] dimensions = new long[]{
                512, 512
        };

        Img<FloatType> imgFinal = ArrayImgs.floats(dimensions);

        ImagePlus[] imps;

        ImagePlus imp;
        try {

            imps = BF.openImagePlus(arg);
            imp = imps[0];
            calibration = imp.getCalibration();
            imp.setDimensions(1,imp.getNFrames(),1);

            imgFinal = ImageJFunctions.convertFloat(imps[0]);
            // for (ImagePlus imp : imps) imp.show();
            // We don't need to show them

        } catch (FormatException | IOException exc) {

            IJ.error("Sorry, an error occurred: " + exc.getMessage());


        }

        return imgFinal;




    }

    private double[][] processing(Img image, String path){
    //private void processing(Img<FloatType> image){


        File theDir = new File(path+"_beads");
        System.out.println("entering create dir");
// if the directory does not exist, create it
        if (!theDir.exists()) {
            System.out.println("creating directory: " + theDir.getName());
            boolean result = false;

            try{
                theDir.mkdir();
                result = true;
            }
            catch(SecurityException se){
                //handle it
            }
            if(result) {
                System.out.println("DIR created");
            }
        }




        IJ.run("Set Measurements...", "min centroid integrated redirect=None decimal=3");
        System.out.println("Opened file, processing");

        //ImageJFunctions.show(image);
        // Crops the image to get middle of the field of view

        long minx, miny, minz, maxx, maxy, maxz;
        minx = 0;
        miny = 0;
        minz = 0;
        maxx = 300;
        maxy = 300;
        maxz = image.dimension(2);
        if (image.dimension(0)>300){
            minx = image.dimension(0)/2-150;
        }
        if (image.dimension(1)>300){
            miny = image.dimension(1)/2-150;
        }

        FinalInterval interval = FinalInterval.createMinSize(minx,miny,minz,maxx,maxy,maxz);



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

        ImagePlus imp = IJ.getImage();
        MaximumFinder mf = new MaximumFinder();
        Polygon pol = mf.getMaxima(imp.getProcessor(),(int)noiseTol,true);

        // detect beads and measure for intensity and x/y coords
        //IJ.run("Find Maxima...", "noise="+noiseTol+" output=[Point Selection] exclude");
        //ImagePlus imp = IJ.getImage();
        //IJ.saveAsTiff(imp,path+"_beads"+File.separator+"allbeads"+".tif");
        // Gets coordinates of ROIs

        //Roi test = imp.getRoi();
        //FloatPolygon floatPolygon = test.getFloatPolygon();
        float[][] resultsTable = new float[pol.npoints][3];

        // loops over ROIs and get pixel Vvlue at their coordinate.
        for (int i=0; i < pol.npoints; i++){

            float intx = pol.xpoints[i];
            float inty = pol.ypoints[i];
            final RandomAccess<FloatType> r = proj.randomAccess();
            r.setPosition((int) intx,0);
            r.setPosition((int) inty,1);
            FloatType pixel = r.get();

            resultsTable[i][0] = intx;
            resultsTable[i][1] = inty;
            resultsTable[i][2] = pixel.get();

        }



        // Sorts the Pixel coordinates by the intensity value.
        java.util.Arrays.sort(resultsTable, Comparator.comparingDouble(a -> a[2]));

        int countSpots = 0;
        int firstPosition = 0;
        double[] goodX = new double[beads];
        double[] goodY = new double[beads];




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
                Roi beadROI  = new Roi(resultsTable[firstPosition][0]-15,resultsTable[firstPosition][1]-15,30,30);
                ImageProcessor ip = imp.getProcessor();
                //ip.setRoi(beadROI);
                ip.setValue(100000);
                ip.draw(beadROI);
                goodX[countSpots] = resultsTable[firstPosition][0];
                goodY[countSpots] = resultsTable[firstPosition][1];
                countSpots++;

            }

            firstPosition++;

        }


        IJ.saveAsTiff(imp,path+"_beads"+File.separator+"allbeads"+".tif");
        double[][] finalResults  = new double[beads][4];




        long cropSize = 20;
        String unit = calibration.getUnit();
        if (unit.equals("micron") || unit.equals("um")){
            double pixSize = calibration.pixelHeight;
            cropSize = round((3 * beadSize) / pixSize);
        }
        if (unit.equals("nm")){
            double pixSize = calibration.pixelHeight;
            cropSize = round((3 * beadSize) / (pixSize/1000));
        }
        if (cropSize < 20){
            cropSize = 20;
        }

        //ij.ui().showUI();
        // loops over selected pixels and crops out the PSFs
        for (int i = 0; i < goodX.length; i++){


            minx = 0;
            miny = 0;
            minz = 0;
            maxx = cropSize;
            maxy = cropSize;
            maxz = cropped.dimension(2);
            if (goodX[i]>cropSize/2 && goodX[i]<cropped.dimension(0)-cropSize){
                minx = (long) ceil(goodX[i]-cropSize/2);
            }
            if (goodY[i]>cropSize/2 && goodY[i]<cropped.dimension(1)-cropSize){
                miny = (long) ceil(goodY[i]-cropSize/2);
            }

            if (goodX[i]>=cropped.dimension(0)-cropSize){
                minx = cropped.dimension(0)-cropSize;
            }

            if (goodY[i]>=cropped.dimension(1)-cropSize){
                miny = cropped.dimension(1)-cropSize;
            }
            interval = FinalInterval.createMinSize(minx,miny,minz,maxx,maxy,maxz);

            //interval = FinalInterval.createMinSize(0,0,0,cropSize,cropSize,cropped.dimension(2));


            RandomAccessibleInterval newcropped  = ij.op().transform().crop(cropped,interval, true);
            ImagePlus IPcropped = ImageJFunctions.wrapFloat(newcropped, "test");
            IPcropped.setDimensions(1, (int) newcropped.dimension(2), 1);
            IPcropped.setOpenAsHyperStack(true);
            ImageProcessor ip = IPcropped.getProcessor();
            ip.resetMinAndMax();

            IPcropped.setCalibration(calibration);


            FileSaver fs = new FileSaver(IPcropped);
            fs.saveAsTiff(path+"_beads"+File.separator+"bead_"+i+".tif");
            // crops stack around the specified coordinates

            // calls GetRes to extract the resolution form the PSFs
            double[] qcMetrics = GetRes(IPcropped);


            // multiply by the correction factor
            double xRes = qcMetrics[0] * corr_factor_x;
            double yRes = qcMetrics[1] * corr_factor_y;
            double zRes = qcMetrics[2] * corr_factor_z;

            finalResults[i][0] = i;
            finalResults[i][1] = xRes;
            finalResults[i][2] = yRes;
            finalResults[i][3] = zRes;


        }

        String[] titles = getImageTitles();
        for (String title:titles){
            Window window = getWindow(title);
            window.dispose();
            removeWindow(window);
        }
        return finalResults;

    }


    private static void WriteFile(String FilePath, String filename, double[][] BeatResArray){

            String COMMA_DELIMITER = ",";
            String NEW_LINE_SEPARATOR = "\n";
            FileWriter fileWriter = null;
            try {
                fileWriter = new FileWriter(FilePath);
                //Write the CSV file header
                fileWriter.append("filename").append("bead_id").append("x_resolution").append(COMMA_DELIMITER).append("y_resolution").append(COMMA_DELIMITER).append("z_resolution");
                //Add a new line separator after the header
                fileWriter.append(NEW_LINE_SEPARATOR);
                for (double[] doubles : BeatResArray) {
                    fileWriter.append(filename);
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(String.valueOf(doubles[0]));
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(String.valueOf(doubles[1]));
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(String.valueOf(doubles[2]));
                    fileWriter.append(COMMA_DELIMITER);
                    fileWriter.append(String.valueOf(doubles[3]));
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


    }

    private static double[] GetRes(ImagePlus BeatStack){

        PSFprofiler profiler=new PSFprofiler(BeatStack);
        return profiler.getResolutions();

    }


    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
     *
     *
     */
    public static void main(final String... args) {
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


