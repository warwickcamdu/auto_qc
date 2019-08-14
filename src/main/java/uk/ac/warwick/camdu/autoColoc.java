package uk.ac.warwick.camdu;


import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.ChannelSplitter;
import ij.plugin.filter.MaximumFinder;
import ij.process.ImageProcessor;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import net.imagej.ImageJ;
import net.imagej.ops.Ops;
import net.imagej.ops.special.computer.Computers;
import net.imagej.ops.special.computer.UnaryComputerOp;
import net.imglib2.FinalDimensions;
import net.imglib2.FinalInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.Img;
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
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import static ij.WindowManager.*;
import static java.lang.Math.ceil;
import static java.lang.Math.round;


/**
 *
 * autoColoc - Fiji routine to generate colocalization shifts from an image containing beads
 *<p>
 * This class implements a Fiji routine that reads image files, detects beads, crops them, creates a coAlignment
 * object (using MetroloJ code) and retrieves the inter-channel shifts for each individual bead. Finally, it
 * saves the results on a spreadsheet, identifying from which files and bead IDs they come, and saves a maximum
 * projection indicating the chosen beads and individual tiff files for each selected bead.
 *</p>
 * @param <T> I don't think we actually use this
 * @author Erick Martins Ratamero
 * @version 1.0
 */

@SuppressWarnings("unchecked")
@Plugin(type = Command.class, menuPath = "Plugins>autoQC>autoColoc")
public class autoColoc<T extends RealType<T>> extends Component implements Command {

    @Parameter
    private ImageJ ij;


    /**
     * beads : integer, number of beads to be processed per file
     */
    @Parameter(label = "number of beads:")
    private int beads = 3;
    /**
     * beadSize : double, estimated size of beads in microns. Defines size of crop.
     */
    @Parameter(label = "Bead size (um):")
    private double beadSize = 1.0;
    /**
     * noiseTol : double, value to be used by the Find Maxima routine. Higher means fewer maxima are detected.
     */
    @Parameter(label = "Noise threshold:")
    private double noiseTol = 100;
    /**
     * minSeparation: integer, minimum pixel distance between maxima for them to be valid
     */
    @Parameter(label = "Minimum bead separation (px):")
    private int minSeparation = 15;


    private Calibration calibration;

    private static final String COMMA_DELIMITER = ",";
    private static final String NEW_LINE_SEPARATOR = "\n";
    /**
     * srcDir: list of files to be processed.
     */
    @Parameter(style="files", label = "select files:")
    private File[] srcDir;

    @Parameter(label = "(Optional) match string for filename:")
    private String match = "";

    /**
     * setBeads: only used when running this as a Java program rather than in Fiji.
     * @param beadnum number of beads
     */
    private void setBeads(int beadnum){
        beads = beadnum;

    }
    /**
     * setBeadSize: only used when running this as a Java program rather than in Fiji.
     * @param bsize bead size in um
     */
    private void setBeadSize(double bsize){
        beadSize = bsize;

    }
    /**
     * setMinSep: only used when running this as a Java program rather than in Fiji.
     * @param minsep minimum bead separation in pixels
     */
    private void setMinSep(int minsep){
        minSeparation = minsep;

    }
    /**
     * setNoiseTol: only used when running this as a Java program rather than in Fiji.
     * @param ntol noise tolerance value
     */
    private void setNoiseTol(double ntol){
        noiseTol = ntol;

    }

    /**
     * setDir: only used when running this as a Java program rather than in Fiji.
     * @param sourceDir directory with source files
     */
    private void setDir(File[] sourceDir){
        srcDir = sourceDir;

    }


    /**
     * setOutputDir: only used when running this from autoQC_OMERO
     * @param outputDir directory where original images are contained
     */
    public void setOutputDir(File outputDir){
        srcDir = new File[1];
        srcDir[0] = outputDir;

    }


/**
 * creates a new FileWriter and writes a header to it. Returns the created FileWriter
 *<p>
 *     We try to create a new Filewriter and add a header to it. If that does't work, we catch the exception and
 *     return a null FileWriter.
 *</p>
 *
 *
 *
 * @param FilePath string with the path to the output file.
 * @return fileWriter an instance of FileWriter pointing to the desired file.
 * */

    private FileWriter printOutputHeader(String FilePath){
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(FilePath);
            //Write the CSV file header
            fileWriter.append("file_id").append(COMMA_DELIMITER).append("bead_id").append(COMMA_DELIMITER).append("red-green").append(COMMA_DELIMITER).append("green-blue").append(COMMA_DELIMITER).append("red-blue");
            //Add a new line separator after the header
            fileWriter.append(NEW_LINE_SEPARATOR);

        } catch (Exception e) {

            System.out.println("Error in CsvFileWriter !!!");
            e.printStackTrace();

        }

        return fileWriter;
    }



    /**
     * createUI: generates an UI if running this as Java program rather than in Fiji
     *
     *<p>
     *  Generates JTextFields and a JButton for inputting parameters. The button does stuff when pressed (more on
     * that later), for the rest it's just a simple JPanel with all the JTextFields. Finally, it uses the set functions
     * to set the class parameters from the text fields.
     *</p>
     *
     *
     */
    private void createUI(){

        JTextField beadField = new JTextField("1",5);
        JTextField beadSizeField = new JTextField("1",5);
        JTextField sepField = new JTextField("30",5);
        JTextField noiseTolField = new JTextField("100",5);


        JButton browseBtn = new JButton("Browse:");

        browseBtn.addActionListener(e -> browseButtonActionPerformed());



        System.out.println(Arrays.toString(srcDir));



        JPanel myPanel = new JPanel();



        myPanel.add(new JLabel("Number of beads:"));
        myPanel.add(beadField);

        myPanel.add(new JLabel("Bead size (um):"));
        myPanel.add(beadSizeField);

        myPanel.add(new JLabel("Minimum bead separation (px):"));
        myPanel.add(sepField);

        myPanel.add(new JLabel("Noise threshold:"));
        myPanel.add(noiseTolField);

        myPanel.add(new JLabel("Please select your files:"));
        myPanel.add(browseBtn);



        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

        JOptionPane.showConfirmDialog(
                null, myPanel, "autoColoc", JOptionPane.OK_CANCEL_OPTION);


        setBeads(Integer.parseInt(beadField.getText()));
        setBeadSize(Double.parseDouble(beadSizeField.getText()));
        setMinSep(Integer.parseInt(sepField.getText()));
        setNoiseTol(Double.parseDouble(noiseTolField.getText()));



    }


    /**
     * createUI_omero: generates an UI if running this as part of autoQC_omero
     *
     *<p>
     *  Generates JTextFields and a JButton for inputting parameters. It's just a simple JPanel with all the
     *  JTextFields. Finally, it uses the set functions to set the class parameters from the text fields.
     *</p>
     *
     *
     */


    private void createUI_omero(){

        JTextField beadField = new JTextField("1",5);
        JTextField beadSizeField = new JTextField("1",5);
        JTextField sepField = new JTextField("30",5);
        JTextField noiseTolField = new JTextField("100",5);



        JPanel myPanel = new JPanel();



        myPanel.add(new JLabel("Number of beads:"));
        myPanel.add(beadField);

        myPanel.add(new JLabel("Bead size (um):"));
        myPanel.add(beadSizeField);

        myPanel.add(new JLabel("Minimum bead separation (px):"));
        myPanel.add(sepField);

        myPanel.add(new JLabel("Noise threshold:"));
        myPanel.add(noiseTolField);




        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

        JOptionPane.showConfirmDialog(
                null, myPanel, "autoColoc", JOptionPane.OK_CANCEL_OPTION);


        setBeads(Integer.parseInt(beadField.getText()));
        setBeadSize(Double.parseDouble(beadSizeField.getText()));
        setMinSep(Integer.parseInt(sepField.getText()));
        setNoiseTol(Double.parseDouble(noiseTolField.getText()));



    }





    /**
     * Defines what happens when the button for selecting files is clicked.
     * <p>
     *     Here, we create a new JFileChooser that can select multiple files, set it at the current directory and
     *     wait for the user to click ok. When they do, we get the selected files and use one of the set functions
     *     to populate the class-wide list of files to be processed.
     * </p>
     */

    private void browseButtonActionPerformed() {

        JFileChooser chooser = new JFileChooser();
        chooser.setMultiSelectionEnabled(true);
        chooser.setCurrentDirectory(new java.io.File("."));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(true);
        //chooser.showOpenDialog(this);
        File[] selectedDir = new File[1];

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedDir = chooser.getSelectedFiles();
            //sourceDir = selectedDir.getAbsolutePath();

        }
        else {
            System.out.println("No Selection ");
        }


        setDir(selectedDir);
    }



    /**
     * main routine function - goes over the list of files, process them and writes the output values
     * <p>
     *     Fairly straightforward method: loops over the list of files that is stored on the class-wide srcDir variable,
     *     check which ones are of the correct extension and contain the string "coloc" (both requirements are probably
     *     obsolete now that the user can directly choose files), reads the files, calls "processing" to get outputs
     *     and finally write these outputs into a file.
     * </p>
     */
    @Override
    public void run() {


        //createUI();

        ij = new net.imagej.ImageJ();
        //String srcDir = selectedDir.getAbsolutePath();

        //System.out.println(srcDir);


        //File selectedDir = srcDir;
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd.HHmmss");
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        List<Img> currentFiles;
        String selectedDir = srcDir[0].getAbsolutePath();
        String resultPath = selectedDir + File.separator + sdf.format(timestamp)+"summary_coloc.csv";
        FileWriter fw = printOutputHeader(resultPath);


        for (final File fileEntry : Objects.requireNonNull(srcDir)){


            //if (fileEntry.getName().contains(match)) {
                System.out.println("Opening file: " + fileEntry.getName());
                String path = fileEntry.getPath();

                currentFiles = readFile(path);
                System.out.println("Processing file: " + fileEntry.getName());

                processing(currentFiles, path, fw, fileEntry.getName());

                System.out.println("Writing output: ");

                //WriteFile(fw, fileEntry.getName(), finalResult);

            //}




        }



        CloseFile(fw);
        // skip irrelevant filenames, do stuff for relevant ones


    }


    /**
     * Simple wrapper for creating a directory
     *<p>
     * If the desired directory doesn't exist, create it. That's it.
     *</p>

     @param dir File object with the desired path for the directory to be created
     */

    private void createDirectory(File dir){

        if (!dir.exists()) {
            dir.mkdirs();

        }
    }


    /**
     * main routine function - goes over the list of files, process them and writes the output values
     * <p>
     *     Fairly straightforward method: loops over the list of files that is given as input, calls "processing" to
     *     get outputs and write files.
     * </p>
     * @param list_images List of Img objects with the images to be processed
     * @param filenames List of Strings with the filenames of the images to be processed
     * @return filename time-stamped filename for the CSV output file
     */

    public String run_omero(List<Img> list_images, List<String> filenames){


        createUI_omero();

        ij = new net.imagej.ImageJ();
        //String srcDir = selectedDir.getAbsolutePath();

        //System.out.println(srcDir);



        //File[] selectedDir =srcDir;
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd.HHmmss");
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        createDirectory(new File(srcDir[0]+"_colocresults/"));

        String resultPath = srcDir[0]+"_colocresults"+"/"+ sdf.format(timestamp)+"summary_coloc.csv";
        FileWriter fw = printOutputHeader(resultPath);



        processing_omero(list_images, srcDir[0].toString(), filenames, fw);
        System.out.println("Writing output: ");


        //WriteFile(fw, filename, finalResult);

        CloseFile(fw);
        //}

    return sdf.format(timestamp)+"summary_coloc.csv";

    }


    /**
     * Reads a string with the path to an image file and returns an Img object.
     * <p>
     *     We use bioformats to open an ImagePlus, make sure that the input is a Z-stack, then generate an Img
     *     converting the input to floats. If any of that fails, we catch an exception.
     * </p>
     *
     * @param arg String with the path to file to be read.
     * @return imgFinal Img object with a Z-stack from the input file.
     */
    public java.util.List<Img> readFile(String arg) {

        //  OpenDialog od = new OpenDialog("Open Image File...", arg);
        //  String dir = od.getDirectory();
        //  String name = od.getFileName();
        //  String id = dir + name;

        List<Img> toReturn = new ArrayList<>();

        ImagePlus[] imps;

        //ImagePlusReader test = new ImagePlusReader();

        ImagePlus imp = new ImagePlus();
        try {
            //ImageReader ir = new ImageReader();
            //ir.setId(arg);
            ImporterOptions options = new ImporterOptions();
            options.setId(arg);
            options.setOpenAllSeries(true);
            ImportProcess process = new ImportProcess(options);
            process.execute();
            options.setOpenAllSeries(false);
            int i;
            imps = new ImagePlus[1];
            for (i = 0; i < process.getSeriesCount(); i++) {
                if(process.getSeriesLabel(i).contains(match)){
                    //System.out.println(process.getSeriesLabel(i));
                    ImporterOptions int_options = new ImporterOptions();
                    int_options.setId(arg);
                    int_options.setSeriesOn(i,true);
                    imps = BF.openImagePlus(int_options);
                    imp = imps[0];
                    //System.out.println(imp.getProperties().toString());
                    calibration = imp.getCalibration();
                    if (imp.getNDimensions() != 4){
                        IJ.error("Number of image dimensions is different from 4");
                        return null;
                    }
                    if (imp.getDimensions()[2] > 3){
                        System.out.println("WARNING: number of channels is larger than 3. Make sure your dimensions are in the right order!");
                    }

                    toReturn.add(ImageJFunctions.convertFloat(imps[0]));
                }
            }


        }catch (FormatException | IOException exc) {

            IJ.error("Sorry, an error occurred: " + exc.getMessage());
            imps = new ImagePlus[1];

        }

        calibration = imp.getCalibration();

        // for (ImagePlus imp : imps) imp.show();
        // We don't need to show them



        return toReturn;




    }



    /**
     * Does the meat of the processing routine - takes an Img, returns a double[][] matrix with all the results
     *<p>
     * This function gets an Img and a string with the path to th original file. From that, it tries to create a
     * directory for the outputs (beads, indication of where the chosen beads were). Then, it crops a 300x300 area at
     * the centre of the image, does a maximum projection of the original Z-stack for channel 1, finds maxima on it.
     * Then, it goes through each maximum, checks it's valid (given minimum separation between maxima) and, for the
     * valid ones up to the number of desired beads, crops them, split the channels (MetroloJ wants that...), runs
     * coAlignment and retrieves the channel shift values.
     * Finally, it returns a matrix with bead IDs, X/Y/Z shifts for that input file.
     *</p>
     * @param images Img object with the input Z-stack
     * @param path String with the path to the original image file that is being processed
     *
     */

    private void processing(List<Img> images, String path, FileWriter fw, String name){
        //private void processing(Img<FloatType> image){

        double[][][] toReturn = new double[images.size()][][];
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

        int j;


        for (j=0;j<images.size();j++) {
            Img image = images.get(j);

            long minx, miny, maxx, maxy;
            minx = 0;
            miny = 0;

            maxx = 300;
            maxy = 300;


            if (image.dimension(0) > 300) {
                minx = image.dimension(0) / 2 - 150;
            }
            if (image.dimension(1) > 300) {
                miny = image.dimension(1) / 2 - 150;
            }

            FinalInterval interval = FinalInterval.createMinSize(minx, miny, 0, 0, maxx, maxy, image.dimension(2), image.dimension(3));


            RandomAccessibleInterval cropped;
            cropped = ij.op().transform().crop(image, interval, true);
            //        ImageJFunctions.show(cropped);
            int[] projected_dimensions = new int[cropped.numDimensions() - 1];

            int dim = 3;
            int d;
            for (d = 0; d < cropped.numDimensions(); ++d) {
                if (d != dim) projected_dimensions[d] = (int) cropped.dimension(d);
            }

            Img<FloatType> proj = ij.op().create().img(
                    new FinalDimensions(projected_dimensions), new FloatType());

            UnaryComputerOp maxOp = Computers.unary(ij.op(), Ops.Stats.Max.class, RealType.class, Iterable.class);

            Img<T> projection = (Img<T>) ij.op().transform().project(proj, cropped, maxOp, 3);

            interval = FinalInterval.createMinSize(0, 0, 0, proj.dimension(0), proj.dimension(1), 1);
            RandomAccessibleInterval finalcrop;
            finalcrop = ij.op().transform().crop(proj, interval, true);
            ImageJFunctions.show(finalcrop);
            ImagePlus imp = IJ.getImage();
            MaximumFinder mf = new MaximumFinder();
            Polygon pol = mf.getMaxima(imp.getProcessor(), (int) noiseTol, true);

            // detect beads and measure for intensity and x/y coords
            //IJ.run("Find Maxima...", "noise="+noiseTol+" output=[Point Selection] exclude");
            //ImagePlus imp = IJ.getImage();
            //IJ.saveAsTiff(imp,path+"_beads"+File.separator+"allbeads"+".tif");
            // Gets coordinates of ROIs

            //Roi test = imp.getRoi();
            //FloatPolygon floatPolygon = test.getFloatPolygon();
            float[][] resultsTable = new float[pol.npoints][3];

            // loops over ROIs and get pixel Vvlue at their coordinate.
            for (int i = 0; i < pol.npoints; i++) {

                float intx = pol.xpoints[i];
                float inty = pol.ypoints[i];
                final RandomAccess<FloatType> r = proj.randomAccess();
                r.setPosition((int) intx, 0);
                r.setPosition((int) inty, 1);
                FloatType pixel = r.get();

                resultsTable[i][0] = intx;
                resultsTable[i][1] = inty;
                resultsTable[i][2] = pixel.get();

            }


            // Sorts the Pixel coordinates by the intensity value.
            //java.util.Arrays.sort(resultsTable, Comparator.comparingDouble(a -> a[2]));
            java.util.Arrays.sort(resultsTable, (v1, v2) -> Float.compare(v2[2], v1[2]));

            int countSpots = 0;
            int firstPosition = 0;
            double[] goodX = new double[beads];
            double[] goodY = new double[beads];


            // selects the selected number of pixels based on the specified criteria
            while (countSpots < beads && firstPosition < resultsTable.length) {

                float x1 = resultsTable[firstPosition][0];

                float y1 = resultsTable[firstPosition][1];

                int nextPosition = firstPosition + 1;
                boolean valid = true;

                while (valid && nextPosition < resultsTable.length) {

                    float x2 = resultsTable[nextPosition][0];
                    float y2 = resultsTable[nextPosition][1];

                    double dist_sq = Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2);

                    if (x2 != x1 && y2 != y1 && dist_sq < Math.pow(minSeparation, 2)) {

                        valid = false;

                    }

                    nextPosition++;


                }


                if (valid) {
                    Roi beadROI = new Roi(resultsTable[firstPosition][0] - 15, resultsTable[firstPosition][1] - 15, 30, 30);
                    ImageProcessor ip = imp.getProcessor();
                    //ip.setRoi(beadROI);
                    ip.setValue(10000);
                    ip.draw(beadROI);
                    goodX[countSpots] = resultsTable[firstPosition][0];
                    goodY[countSpots] = resultsTable[firstPosition][1];
                    countSpots++;

                }

                firstPosition++;

            }

            IJ.saveAsTiff(imp, path + "_beads" + File.separator + "allbeads" + ".tif");

            double[][] finalResults = new double[beads][4];


            long cropSize = 35;
            String unit = calibration.getUnit();
            if (unit.equals("micron") || unit.equals("um")) {
                double pixSize = calibration.pixelHeight;
                cropSize = round((3 * beadSize) / pixSize);
            }
            if (unit.equals("nm")) {
                double pixSize = calibration.pixelHeight;
                cropSize = round((3 * beadSize) / (pixSize / 1000));
            }
            if (cropSize < 35) {
                cropSize = 35;
            }


            //ij.ui().showUI();
            // loops over selected pixels and crops out the PSFs
            for (int i = 0; i < goodX.length; i++) {

                minx = 0;
                miny = 0;
                maxx = cropSize;
                maxy = cropSize;
                if (goodX[i] > cropSize / 2 && goodX[i] < cropped.dimension(0) - cropSize) {
                    minx = (long) ceil(goodX[i] - cropSize / 2);
                }
                if (goodY[i] > cropSize / 2 && goodY[i] < cropped.dimension(1) - cropSize) {
                    miny = (long) ceil(goodY[i] - cropSize / 2);
                }

                if (goodX[i] >= cropped.dimension(0) - cropSize) {
                    minx = cropped.dimension(0) - cropSize;
                }

                if (goodY[i] >= cropped.dimension(1) - cropSize) {
                    miny = cropped.dimension(1) - cropSize;
                }
                interval = FinalInterval.createMinSize(minx, miny, 0, 0, maxx, maxy, cropped.dimension(2), cropped.dimension(3));


                RandomAccessibleInterval newcropped = ij.op().transform().crop(cropped, interval, true);
                ImagePlus IPcropped = ImageJFunctions.wrapFloat(newcropped, "test");
                IPcropped.setDimensions((int) newcropped.dimension(2), (int) newcropped.dimension(3), 1);
                IPcropped.setOpenAsHyperStack(true);
                ImageProcessor ip = IPcropped.getProcessor();
                ip.resetMinAndMax();

                IPcropped.setCalibration(calibration);

                FileSaver fs = new FileSaver(IPcropped);
                fs.saveAsTiff(path + "_beads" + File.separator + "bead_" + i + ".tif");

                // crops stack around the specified coordinates

                // calls GetRes to extract the resolution form the PSFs
                ImagePlus[] input = ChannelSplitter.split(IPcropped);
                double[] colorShifts = GetCoal(input);

                // multiply by the correction factor
                /*double xRes = qcMetrics[0] * corr_factor_x;
                double yRes = qcMetrics[1] * corr_factor_y;
                double zRes = qcMetrics[2] * corr_factor_z;*/
                finalResults[i][0] = i;
                finalResults[i][1] = colorShifts[0];
                finalResults[i][2] = colorShifts[1];
                finalResults[i][3] = colorShifts[2];


            }

            String[] titles = getImageTitles();
            for (String title : titles) {
                Window window = getWindow(title);
                window.dispose();
                removeWindow(window);
            }
            toReturn[j] = finalResults;
            WriteThisFile(fw,name,finalResults);
        }

    }




    /**
     * Does the meat of the processing routine - takes an Img, returns a double[][] matrix with all the results
     *<p>
     * This function gets an Img and a string with the path to th original file. From that, it tries to create a
     * directory for the outputs (beads, indication of where the chosen beads were). Then, it takes the first slice
     * on that image and finds maxima on it.
     * Next, it goes through each maximum, checks it's valid (given minimum separation between maxima) and, for the
     * valid ones up to the number of desired beads, crops them, feeds them into the track() method (that wraps
     * TrackMate) and retrieves the maximum displacement values.
     * Finally, it returns a matrix with bead IDs, X/Y displacements for that input file.
     *</p>
     * @param images List of Img objects with the input Z-stacks
     * @param path String with the path to the original image file that is being processed
     * @param filenames List of Strings with all filenames for the files being processed
     * @param fw FileWriter object for the output CSV file
     *
     */

    private void processing_omero(List<Img> images, String path, List<String> filenames, FileWriter fw){
        //private void processing(Img<FloatType> image){

        double[][][] toReturn = new double[images.size()][][];
        File theDir = new File(path+"_colocresults");
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

        int j;


        for (j=0;j<images.size();j++) {
            Img image = images.get(j);
            String name = filenames.get(j);
            long minx, miny, maxx, maxy;
            minx = 0;
            miny = 0;

            maxx = 300;
            maxy = 300;


            if (image.dimension(0) > 300) {
                minx = image.dimension(0) / 2 - 150;
            }
            if (image.dimension(1) > 300) {
                miny = image.dimension(1) / 2 - 150;
            }

            FinalInterval interval = FinalInterval.createMinSize(minx, miny, 0, 0, maxx, maxy, image.dimension(2), image.dimension(3));


            RandomAccessibleInterval cropped;
            cropped = ij.op().transform().crop(image, interval, true);
            //        ImageJFunctions.show(cropped);
            int[] projected_dimensions = new int[cropped.numDimensions() - 1];

            int dim = 3;
            int d;
            for (d = 0; d < cropped.numDimensions(); ++d) {
                if (d != dim) projected_dimensions[d] = (int) cropped.dimension(d);
            }

            Img<FloatType> proj = ij.op().create().img(
                    new FinalDimensions(projected_dimensions), new FloatType());

            UnaryComputerOp maxOp = Computers.unary(ij.op(), Ops.Stats.Max.class, RealType.class, Iterable.class);

            Img<T> projection = (Img<T>) ij.op().transform().project(proj, cropped, maxOp, 3);

            interval = FinalInterval.createMinSize(0, 0, 0, proj.dimension(0), proj.dimension(1), 1);
            RandomAccessibleInterval finalcrop;
            finalcrop = ij.op().transform().crop(proj, interval, true);
            ImageJFunctions.show(finalcrop);
            ImagePlus imp = IJ.getImage();
            MaximumFinder mf = new MaximumFinder();
            Polygon pol = mf.getMaxima(imp.getProcessor(), (int) noiseTol, true);

            // detect beads and measure for intensity and x/y coords
            //IJ.run("Find Maxima...", "noise="+noiseTol+" output=[Point Selection] exclude");
            //ImagePlus imp = IJ.getImage();
            //IJ.saveAsTiff(imp,path+"_beads"+File.separator+"allbeads"+".tif");
            // Gets coordinates of ROIs

            //Roi test = imp.getRoi();
            //FloatPolygon floatPolygon = test.getFloatPolygon();
            float[][] resultsTable = new float[pol.npoints][3];

            // loops over ROIs and get pixel Vvlue at their coordinate.
            for (int i = 0; i < pol.npoints; i++) {

                float intx = pol.xpoints[i];
                float inty = pol.ypoints[i];
                final RandomAccess<FloatType> r = proj.randomAccess();
                r.setPosition((int) intx, 0);
                r.setPosition((int) inty, 1);
                FloatType pixel = r.get();

                resultsTable[i][0] = intx;
                resultsTable[i][1] = inty;
                resultsTable[i][2] = pixel.get();

            }


            // Sorts the Pixel coordinates by the intensity value.
            //java.util.Arrays.sort(resultsTable, Comparator.comparingDouble(a -> a[2]));
            java.util.Arrays.sort(resultsTable, (v1, v2) -> Float.compare(v2[2], v1[2]));

            int countSpots = 0;
            int firstPosition = 0;
            double[] goodX = new double[beads];
            double[] goodY = new double[beads];


            // selects the selected number of pixels based on the specified criteria
            while (countSpots < beads && firstPosition < resultsTable.length) {

                float x1 = resultsTable[firstPosition][0];

                float y1 = resultsTable[firstPosition][1];

                int nextPosition = firstPosition + 1;
                boolean valid = true;

                while (valid && nextPosition < resultsTable.length) {

                    float x2 = resultsTable[nextPosition][0];
                    float y2 = resultsTable[nextPosition][1];

                    double dist_sq = Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2);

                    if (x2 != x1 && y2 != y1 && dist_sq < Math.pow(minSeparation, 2)) {

                        valid = false;

                    }

                    nextPosition++;


                }


                if (valid) {
                    Roi beadROI = new Roi(resultsTable[firstPosition][0] - 15, resultsTable[firstPosition][1] - 15, 30, 30);
                    ImageProcessor ip = imp.getProcessor();
                    //ip.setRoi(beadROI);
                    ip.setValue(10000);
                    ip.draw(beadROI);
                    goodX[countSpots] = resultsTable[firstPosition][0];
                    goodY[countSpots] = resultsTable[firstPosition][1];
                    countSpots++;

                }

                firstPosition++;

            }

            IJ.saveAsTiff(imp,path+"_colocresults"+File.separator+name+"_allbeads"+".tif");

            double[][] finalResults = new double[beads][4];


            long cropSize = 35;
            String unit = calibration.getUnit();
            if (unit.equals("micron") || unit.equals("um")) {
                double pixSize = calibration.pixelHeight;
                cropSize = round((3 * beadSize) / pixSize);
            }
            if (unit.equals("nm")) {
                double pixSize = calibration.pixelHeight;
                cropSize = round((3 * beadSize) / (pixSize / 1000));
            }
            if (cropSize < 35) {
                cropSize = 35;
            }


            //ij.ui().showUI();
            // loops over selected pixels and crops out the PSFs
            for (int i = 0; i < goodX.length; i++) {

                minx = 0;
                miny = 0;
                maxx = cropSize;
                maxy = cropSize;
                if (goodX[i] > cropSize / 2 && goodX[i] < cropped.dimension(0) - cropSize) {
                    minx = (long) ceil(goodX[i] - cropSize / 2);
                }
                if (goodY[i] > cropSize / 2 && goodY[i] < cropped.dimension(1) - cropSize) {
                    miny = (long) ceil(goodY[i] - cropSize / 2);
                }

                if (goodX[i] >= cropped.dimension(0) - cropSize) {
                    minx = cropped.dimension(0) - cropSize;
                }

                if (goodY[i] >= cropped.dimension(1) - cropSize) {
                    miny = cropped.dimension(1) - cropSize;
                }
                interval = FinalInterval.createMinSize(minx, miny, 0, 0, maxx, maxy, cropped.dimension(2), cropped.dimension(3));


                RandomAccessibleInterval newcropped = ij.op().transform().crop(cropped, interval, true);
                ImagePlus IPcropped = ImageJFunctions.wrapFloat(newcropped, "test");
                IPcropped.setDimensions((int) newcropped.dimension(2), (int) newcropped.dimension(3), 1);
                IPcropped.setOpenAsHyperStack(true);
                ImageProcessor ip = IPcropped.getProcessor();
                ip.resetMinAndMax();

                IPcropped.setCalibration(calibration);

                FileSaver fs = new FileSaver(IPcropped);
                fs.saveAsTiff(path+"_colocresults"+File.separator+name+"_bead_"+i+".tif");

                // crops stack around the specified coordinates

                // calls GetRes to extract the resolution form the PSFs
                ImagePlus[] input = ChannelSplitter.split(IPcropped);
                double[] colorShifts = GetCoal(input);

                // multiply by the correction factor
                /*double xRes = qcMetrics[0] * corr_factor_x;
                double yRes = qcMetrics[1] * corr_factor_y;
                double zRes = qcMetrics[2] * corr_factor_z;*/
                finalResults[i][0] = i;
                finalResults[i][1] = colorShifts[0];
                finalResults[i][2] = colorShifts[1];
                finalResults[i][3] = colorShifts[2];


            }

            String[] titles = getImageTitles();
            for (String title : titles) {
                Window window = getWindow(title);
                window.dispose();
                removeWindow(window);
            }
            toReturn[j] = finalResults;
            WriteThisFile(fw,name,finalResults);
        }

    }

    /**
     * Writes a matrix to an output file.
     *<p>
     * Given a FileWriter, we append the filename of the image that was processed and the results for each bead
     * processed in that image.
     *</p>
     * @param fileWriter FileWriter object for the output file
     * @param filename string with the filename of the image currently being processed
     * @param BeadResArray double[][] matrix with the results for the current image
     */



    private static void WriteThisFile(FileWriter fileWriter, String filename, double[][] BeadResArray){


        try {

            //Add a new line separator after the header
            fileWriter.append(NEW_LINE_SEPARATOR);
            for (double[] doubles : BeadResArray) {
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

        }

    }


    /**
     * Flushes and closes the input FileWriter object. Catches exception in case it goes wrong.
     * @param fileWriter FileWriter object to be closed
     *
     */
    private void CloseFile(FileWriter fileWriter){
        try {

                assert fileWriter != null;
                fileWriter.flush();
                fileWriter.close();

            } catch (IOException e) {

                System.out.println("Error while flushing/closing fileWriter !!!");
                e.printStackTrace();

            }
    }


    /**
     * Wrapper for creating a PSFProfiler and returning the resolutions.
     * @param beadStack ImagePlus[] with a bead crop and split channels (MetroloJ requires ImagePlus to work)
     * @return shifts a double[] array with the colour shift values
     */
    private static double[] GetCoal(ImagePlus[] beadStack){
        int channels = beadStack.length;
        System.out.println(channels);
        microscope[] micro = new microscope[channels];
        int i;
        for (i=0;i<channels;i++){
            micro[i] = new microscope(beadStack[0].getCalibration(),microscope.WIDEFIELD,500,1.4,0.0,"","");
        }
        coAlignement coal =new coAlignement(beadStack,micro);
        double[] shifts = new double[3];
        shifts[0] = coal.RGDistCal;
        shifts[1] = coal.GBDistCal;
        shifts[2] = coal.RBDistCal;
        return shifts;

    }


    /**
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
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
        autoColoc main_class = new autoColoc();
        main_class.run();
    }
}



