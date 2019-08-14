

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
 * autoPSF - Fiji routine to generate resolution values from an image containing beads
 *<p>
 * This class implements a Fiji routine that reads image files, detects beads, crops them, creates a PSFProfiler
 * object (using MetroloJ code) and retrieves the X/Y/Z FWHM resolutions for each individual bead. Finally, it
 * saves the results on a spreadsheet, identifying from which files and bead IDs they come, and saves a maximum
 * projection indicating the chosen beads and individual tiff files for each selected bead.
 *</p>
 * @param <T> I don't think we actually use this
 * @author Erick Martins Ratamero
 * @version 1.0
 */

@Plugin(type = Command.class, menuPath = "Plugins>autoQC>autoPSF")
public class autoPSF<T extends RealType<T>> extends Component implements Command {


    private static final String COMMA_DELIMITER = ",";
    private static final String NEW_LINE_SEPARATOR = "\n";

    /**
     * All parameters are the user-defined inputs from Fiji
     */

    @Parameter
    private ImageJ ij;


    /**
     * beads : integer, number of beads to be processed per file
     */
    @Parameter(label = "number of beads:")
    private int beads = 3;
    /**
     * corr_factor_x/y/z : doubles, correction factor from FWHM resolution values (in case different definition of
     *                     resolution, or wrong metadata, etc etc)
     */
    @Parameter(label = "Correction factor (x):")
    private double corr_factor_x = 1.186;
    /**
     * corr_factor_x/y/z : doubles, correction factor from FWHM resolution values (in case different definition of
     *                      resolution, or wrong metadata, etc etc)
     */
    @Parameter(label = "Correction factor (y):")
    private double corr_factor_y = 1.186;
    /**
     * corr_factor_x/y/z : doubles, correction factor from FWHM resolution values (in case different definition of
     *                     resolution, or wrong metadata, etc etc)
     */
    @Parameter(label = "Correction factor (z):")
    private double corr_factor_z = 1.186;
    /**
     * beadSize : double, estimated size of beads in microns. Defines size of crop.
     */
    @Parameter(label = "Bead size (um):")
    private double beadSize = 1.0;
    /**
     * channelChoice : int, chooses from which channel (for multichannel images) will be used
     */
    @Parameter(label = "Channel")
    private int channelChoice = 1;
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
    /**
     * srcDir: list of files to be processed.
     */
    @Parameter(style="files", label = "select files:")
    private File[] srcDir;

    @Parameter(label = "(Optional) match string for series:")
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
     * setChannel: only used when running this as a Java program rather than in Fiji.
     * @param channel Channel choice
     */
    private void setChannel(int channel){
        channelChoice = channel;

    }

    /**
     * setCorrX: only used when running this as a Java program rather than in Fiji.
     * @param corr_x correction factor in X
     */
    private void setCorrX(double corr_x){
        corr_factor_x = corr_x;

    }

    /**
     * setCorrY: only used when running this as a Java program rather than in Fiji.
     * @param corr_y correction factor in Y
     */
    private void setCorrY(double corr_y){
        corr_factor_y = corr_y;

    }

    /**
     * setCorrZ: only used when running this as a Java program rather than in Fiji.
     * @param corr_z correction factor in Z
     */
    private void setCorrZ(double corr_z){
        corr_factor_z = corr_z;

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
     * @param sourceDir directory where source images are
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

        JTextField beadField = new JTextField("5",5);
        JTextField beadSizeField = new JTextField("1",5);
        JTextField channelField = new JTextField("1",5);
        JTextField corrXField = new JTextField("1.168",5);
        JTextField corrYField = new JTextField("1.168",5);
        JTextField corrZField = new JTextField("1.168",5);
        JTextField sepField = new JTextField("15",5);
        JTextField noiseTolField = new JTextField("100",5);

        JButton browseBtn = new JButton("Browse:");

        browseBtn.addActionListener(e -> browseButtonActionPerformed());



        System.out.println(Arrays.toString(srcDir));



        JPanel myPanel = new JPanel();


        myPanel.add(new JLabel("Number of beads:"));
        myPanel.add(beadField);

        myPanel.add(new JLabel("Bead size (um):"));
        myPanel.add(beadSizeField);

        myPanel.add(new JLabel("Channel"));
        myPanel.add(channelField);

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

        myPanel.add(new JLabel("Please select your files:"));
        myPanel.add(browseBtn);

        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

        JOptionPane.showConfirmDialog(
                null, myPanel, "autoColoc", JOptionPane.OK_CANCEL_OPTION);


        setBeads(Integer.parseInt(beadField.getText()));
        setCorrX(Double.parseDouble(corrXField.getText()));
        setCorrY(Double.parseDouble(corrYField.getText()));
        setCorrZ(Double.parseDouble(corrZField.getText()));
        setBeadSize(Double.parseDouble(beadSizeField.getText()));
        setChannel(Integer.parseInt(channelField.getText()));
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

        JTextField beadField = new JTextField("5",5);
        JTextField beadSizeField = new JTextField("1",5);
        JTextField corrXField = new JTextField("1.168",5);
        JTextField corrYField = new JTextField("1.168",5);
        JTextField corrZField = new JTextField("1.168",5);
        JTextField channelField = new JTextField("1",5);
        JTextField sepField = new JTextField("15",5);
        JTextField noiseTolField = new JTextField("100",5);





        System.out.println(Arrays.toString(srcDir));



        JPanel myPanel = new JPanel();


        myPanel.add(new JLabel("Number of beads:"));
        myPanel.add(beadField);

        myPanel.add(new JLabel("Bead size (um):"));
        myPanel.add(beadSizeField);

        myPanel.add(new JLabel("Channel"));
        myPanel.add(channelField);

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



        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

        JOptionPane.showConfirmDialog(
                null, myPanel, "autoPSF", JOptionPane.OK_CANCEL_OPTION);


        setBeads(Integer.parseInt(beadField.getText()));
        setCorrX(Double.parseDouble(corrXField.getText()));
        setCorrY(Double.parseDouble(corrYField.getText()));
        setCorrZ(Double.parseDouble(corrZField.getText()));
        setBeadSize(Double.parseDouble(beadSizeField.getText()));
        setChannel(Integer.parseInt(channelField.getText()));
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
     */
    private FileWriter printOutputHeader(String FilePath){
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(FilePath);
            //Write the CSV file header
            fileWriter.append("filename").append(COMMA_DELIMITER).append("bead_id").append(COMMA_DELIMITER).append("x_resolution").append(COMMA_DELIMITER).append("y_resolution").append(COMMA_DELIMITER).append("z_resolution");
            //Add a new line separator after the header
            fileWriter.append(NEW_LINE_SEPARATOR);

        } catch (Exception e) {

            System.out.println("Error in CsvFileWriter !!!");
            e.printStackTrace();

        }

        return fileWriter;
    }


    /**
     * main routine function - goes over the list of files, process them and writes the output values
     * <p>
     *     Fairly straightforward method: loops over the list of files that is stored on the class-wide srcDir variable,
     *     check which ones are of the correct extension and contain the string "psf" (both requirements are probably
     *     obsolete now that the user can directly choose files), reads the files, calls "processing" to get outputs
     *     and finally write these outputs into a file.
     * </p>
     */
    public void run() {


//        createUI();

        ij = new net.imagej.ImageJ();
        //String srcDir = selectedDir.getAbsolutePath();

        //System.out.println(srcDir);

        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd.HHmmss");
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());

        //File[] selectedDir =srcDir;
        String selectedDir = srcDir[0].getParent();
        String resultPath = selectedDir + File.separator + sdf.format(timestamp)+"summary_PSF.csv";
        FileWriter fw = printOutputHeader(resultPath);
        List<Img> currentFiles;

        for (final File fileEntry : Objects.requireNonNull(srcDir)){

            //if (fileEntry.getName().contains(match)) {

                System.out.println("Opening file: " + fileEntry.getName());
                String path = fileEntry.getPath();

                currentFiles = readFile(path);
                if (currentFiles == null) {
                    continue;
                }
                System.out.println("Processing file: " + fileEntry.getName());

                processing(currentFiles, path, fw,fileEntry.getName() );
                System.out.println("Writing output: ");


                //WriteFile(fw, fileEntry.getName(), finalResult);


            //}



        }


        // skip irrelevant filenames, do stuff for relevant ones

        CloseFile(fw);
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
        createDirectory(new File(srcDir[0]+"_PSFresults/"));
        String resultPath = srcDir[0]+"_PSFresults/"+ sdf.format(timestamp)+"summary_PSF.csv";
        FileWriter fw = printOutputHeader(resultPath);



        processing_omero(list_images, srcDir[0].toString(), filenames,fw);
        System.out.println("Writing output: ");


       // WriteFile(fw, filename, finalResult);

        CloseFile(fw);
            //}

        return sdf.format(timestamp)+"summary_PSF.csv";

        }


        // skip irrelevant filenames, do stuff for relevant ones






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
                    if (imp.getNDimensions() < 3){
                        IJ.error("Number of image dimensions is less than 3");
                        return null;
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
     * the centre of the image, does a maximum projection of the original Z-stack, finds maxima on it. Then, it goes
     * through each maximum, checks it's valid (given minimum separation between maxima) and, for the valid ones up
     * to the number of desired beads, crops them, runs PSFProfiler and retrieves the resolution values. Finally,
     * it returns a matrix with bead IDs, X/Y/Z resolutions for that input file.
     *</p>
     * @param images Img object with the input Z-stack
     * @param path String with the path to the original image file that is being processed
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
        // Crops the image to get middle of the field of view; also takes only the selected channel
        int j;


        for (j=0;j<images.size();j++){
            Img image = images.get(j);
            if (channelChoice > image.numDimensions()){
                channelChoice = image.numDimensions();
            }
            long minx, miny, minz, maxx, maxy, maxz;
            minx = 0;
            miny = 0;
            minz = 0;
            maxx = 300;
            maxy = 300;
            maxz = image.dimension(image.numDimensions()-1);


            if (image.dimension(0)>300){
                minx = image.dimension(0)/2-150;
            }
            if (image.dimension(1)>300){
                miny = image.dimension(1)/2-150;
            }
            FinalInterval interval;
            int i;


            if (image.numDimensions() > 3){
                interval = FinalInterval.createMinSize(minx,miny,channelChoice-1,minz,maxx,maxy,1,maxz);
                //System.out.printf("%d, %d, %d, %d, %d, %d, %d, %d",minx,miny,channelChoice,minz,maxx,maxy,channelChoice,maxz);

            }else{
                interval = FinalInterval.createMinSize(minx,miny,minz,maxx,maxy,maxz);
                //System.out.printf("%d, %d, %d, %d, %d, %d",minx,miny,minz,maxx,maxy,maxz);
            }



            RandomAccessibleInterval cropped;
            cropped  = ij.op().transform().crop(image,interval, true);
            System.out.println(cropped.numDimensions());
//        ImageJFunctions.show(cropped);
            int[] projected_dimensions;
//            if (image.numDimensions() > 3){
//                projected_dimensions = new int[cropped.numDimensions() - 2];
//            }else{
            projected_dimensions = new int[cropped.numDimensions() - 1];
//            }


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
            for (i=0; i < pol.npoints; i++){

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
            //java.util.Arrays.sort(resultsTable, Comparator.comparingDouble(a -> a[2]));
            java.util.Arrays.sort(resultsTable, (v1,v2)->Float.compare(v2[2],v1[2]));


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
            for (i = 0; i < goodX.length; i++){


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
                System.out.println(calibration.pixelDepth);
                System.out.println(calibration.toString());


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
        File theDir = new File(path+"_PSFresults");
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


        for (j=0;j<images.size();j++){
            Img image = images.get(j);
            String name = filenames.get(j);

            long minx, miny, minz, maxx, maxy, maxz;
            minx = 0;
            miny = 0;
            minz = 0;
            maxx = 300;
            maxy = 300;
            maxz = image.dimension(image.numDimensions()-1);
            if (channelChoice > image.numDimensions()){
                channelChoice = image.numDimensions();
            }

            if (image.dimension(0)>300){
                minx = image.dimension(0)/2-150;
            }
            if (image.dimension(1)>300){
                miny = image.dimension(1)/2-150;
            }
            FinalInterval interval;
            int i;


            if (image.numDimensions() > 3){
                interval = FinalInterval.createMinSize(minx,miny,channelChoice-1,minz,maxx,maxy,1,maxz);
                //System.out.printf("%d, %d, %d, %d, %d, %d, %d, %d",minx,miny,channelChoice,minz,maxx,maxy,channelChoice,maxz);

            }else{
                interval = FinalInterval.createMinSize(minx,miny,minz,maxx,maxy,maxz);
                //System.out.printf("%d, %d, %d, %d, %d, %d",minx,miny,minz,maxx,maxy,maxz);
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

            ImagePlus imp = IJ.getImage();
            MaximumFinder mf = new MaximumFinder();
            Polygon pol = mf.getMaxima(imp.getProcessor(),(int)noiseTol,true);

            System.out.println("number of points detected:");
            System.out.println(pol.npoints);
            // detect beads and measure for intensity and x/y coords
            //IJ.run("Find Maxima...", "noise="+noiseTol+" output=[Point Selection] exclude");
            //ImagePlus imp = IJ.getImage();
            //IJ.saveAsTiff(imp,path+"_beads"+File.separator+"allbeads"+".tif");
            // Gets coordinates of ROIs

            //Roi test = imp.getRoi();
            //FloatPolygon floatPolygon = test.getFloatPolygon();
            float[][] resultsTable = new float[pol.npoints][3];

            // loops over ROIs and get pixel Vvlue at their coordinate.
            for (i=0; i < pol.npoints; i++){

                float intx = pol.xpoints[i];
                float inty = pol.ypoints[i];
                System.out.println(i +" "+ intx +" "+ inty);
                final RandomAccess<FloatType> r = proj.randomAccess();
                r.setPosition((int) intx,0);
                r.setPosition((int) inty,1);
                FloatType pixel = r.get();

                resultsTable[i][0] = intx;
                resultsTable[i][1] = inty;
                resultsTable[i][2] = pixel.get();

            }



            // Sorts the Pixel coordinates by the intensity value.
            //java.util.Arrays.sort(resultsTable, Comparator.comparingDouble(a -> a[2]));
            java.util.Arrays.sort(resultsTable, (v1,v2)->Float.compare(v2[2],v1[2]));


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


            IJ.saveAsTiff(imp,path+"_PSFresults"+File.separator+name+"_allbeads"+".tif");
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
            for (i = 0; i < goodX.length; i++){


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
                fs.saveAsTiff(path+"_PSFresults"+File.separator+name+"_bead_"+i+".tif");
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
     * Wrapper for creating a PSFProfiler and returning the resolutions.
     * @param BeadStack ImagePlus with a bead crop (MetroloJ requires ImagePlus to work)
     * @return profiler.getResolutions() a double[] array with the X/Y/Z resolution values
     */
    private static double[] GetRes(ImagePlus BeadStack){

        PSFprofiler profiler=new PSFprofiler(BeadStack);
        return profiler.getResolutions();

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


