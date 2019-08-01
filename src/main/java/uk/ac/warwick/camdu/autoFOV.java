

package uk.ac.warwick.camdu;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.Calibration;
import ij.plugin.ChannelSplitter;
import ij.process.ImageProcessor;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
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


/**
 *
 * autoFOV - Fiji routine to generate field-of-view illumination maximum decrease from an image
 *<p>
 * This class implements a Fiji routine that reads image files, creates a fieldIllumination
 * object (using MetroloJ code) and retrieves the maximum decrease in illumination for the field of view. Finally, it
 * saves the results on a spreadsheet, identifying from which files they come.
 *</p>
 * @param <T> I don't think we actually use this
 * @author Erick Martins Ratamero
 * @version 1.0
 */
@Plugin(type = Command.class, menuPath = "Plugins>autoQC>autoFOV")
public class autoFOV<T extends RealType<T>> extends Component implements Command {

    private static final String COMMA_DELIMITER = ",";
    private static final String NEW_LINE_SEPARATOR = "\n";

    @Parameter
    private ImageJ ij;



    /**
     * srcDir: list of files to be processed.
     */
    @Parameter(style="files", label = "select files:")
    private File[] srcDir;

    @Parameter(label = "(Optional) match string for series:")
    private String match = "";

    private Calibration calibration;





    /**
     * setDir: only used when running this as a Java program rather than in Fiji.
     * @param sourceDir directory containing original images
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
            fileWriter.append("file_id"+COMMA_DELIMITER+"channel"+COMMA_DELIMITER+"minimum_relative_intensity");
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



        JButton browseBtn = new JButton("Browse:");

        browseBtn.addActionListener(e -> browseButtonActionPerformed());



        System.out.println(Arrays.toString(srcDir));



        JPanel myPanel = new JPanel();




        myPanel.add(new JLabel("Please select your datset:"));
        myPanel.add(browseBtn);

        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

        JOptionPane.showConfirmDialog(
                null, myPanel, "autoFOV", JOptionPane.OK_CANCEL_OPTION);






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
     *     check which ones are of the correct extension and contain the string "fov" (both requirements are probably
     *     obsolete now that the user can directly choose files), reads the files, calls "processing" to get outputs
     *     and finally write these outputs into a file.
     * </p>
     */
    @Override
    public void run() {

//        createUI();

        ImageJ ij = new ImageJ();
        //String srcDir = selectedDir.getAbsolutePath();

        //System.out.println(srcDir);



//        File selectedDir = new File(srcDir);
        //File selectedDir = srcDir;
        List<Img> currentFiles;


        String selectedDir = srcDir[0].getParent();
        String resultPath = selectedDir + File.separator + "summary_FOV.csv";
        FileWriter fw = printOutputHeader(resultPath);


        for (final File fileEntry : Objects.requireNonNull(srcDir)){


                //if (fileEntry.getName().contains(match)){
                    System.out.println("Opening file: " + fileEntry.getName());
                    String path = fileEntry.getPath();

                    currentFiles = readFile(path);

                    if (currentFiles == null){
                        continue;
                    }

                    System.out.println("Processing file: " + fileEntry.getName());

                    processing(currentFiles, fw,fileEntry.getName() );

                    System.out.println("Writing output: ");

                    //WriteFile(fw,fileEntry.getName(),finalResult);
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



        ij = new net.imagej.ImageJ();
        //String srcDir = selectedDir.getAbsolutePath();

        //System.out.println(srcDir);



        //File[] selectedDir =srcDir;
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd.HHmmss");
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        createDirectory(new File(srcDir[0]+"_FOVresults/"));
        String resultPath = srcDir[0]+"_FOVresults/"+ sdf.format(timestamp)+"summary_FOV.csv";
        FileWriter fw = printOutputHeader(resultPath);



        processing_omero(list_images, fw, filenames);
        System.out.println("Writing output: ");


        //WriteFile(fw, filename, finalResult);

        CloseFile(fw);
        //}

        return sdf.format(timestamp)+"summary_FOV.csv";

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

    public List<Img> readFile(String arg) {

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
                    if (imp.getNDimensions() > 3){
                        IJ.error("Number of image dimensions is larger than 3");
                        return null;
                    }
                    //imps[0].show();
                    //ImageJFunctions.show((Img)ImageJFunctions.wrapNumeric(imps[0]));
                    toReturn.add(ImageJFunctions.wrapReal(imps[0]));
                    //ImageJFunctions.show(toReturn.get(i));
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
     * Does the meat of the processing routine - takes an Img, returns a single double with the result
     *<p>
     * This function gets an Img. From that, it runs fieldIllumination and parses the result string to get the final
     * result (i.e. maximum decrease in illumination in the field of view).

     *</p>
     * @param images Img object with the input Z-stack

     * @return finalResult double with the maximum decrease in illumination in the field of view
     *
     */

    private double[] processing(List<Img> images, FileWriter fw, String name){
        //private void processing(Img<FloatType> image){

        long resultssize = 0;
        for (int i = 0; i < images.size(); i++){
            if (images.get(i).numDimensions() > 2){
                resultssize = resultssize + images.get(i).dimension(2);
            }else{
                resultssize = resultssize + 1;
            }

        }
        double[] results = new double[(int)resultssize];
        System.out.println("Opened file, processing");
        int i;
        for (i=0;i<images.size();i++) {

            Img image = images.get(i);

            //ImageJFunctions.show(image);
            // Crops the image to get middle of the field of view

            ImagePlus input = ImageJFunctions.wrap(image, "test");
            ImageProcessor ip = input.getProcessor();
            input.trimProcessor();
//
            input.setProcessor(null, ip.convertToShort(false));
            input.setCalibration(calibration);
            ip.resetMinAndMax();


            input.show();
            if (input.getNChannels() > 1){
                ImagePlus[] multiinput = ChannelSplitter.split(input);
                System.out.println(multiinput.length);
                for (int j = 0; j < multiinput.length; j++){
                    ImagePlus newinput = multiinput[j];
                    //newinput.show();
                    fieldIllumination FI = new fieldIllumination(newinput);
                    double finalResult = parseResult(FI.getStringData());
                    results[i] = finalResult;
                    WriteThisFile(fw, name, j+1, finalResult);
                    i++;
                }
            }else {
                fieldIllumination FI = new fieldIllumination(input);
                double finalResult = parseResult(FI.getStringData());
                results[i] = finalResult;
                WriteThisFile(fw, name, 1, finalResult);
            }
            String[] titles = getImageTitles();
            for (String title : titles) {
                Window window = getWindow(title);
                window.dispose();
                removeWindow(window);
            }

        }
        return results;

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
     * @param filenames List of Strings with all filenames for the files being processed
     * @param fw FileWriter object for the output CSV file
     * @return finalResults double[] matrix with all the displacement results for all the beads in all images
     *
     */

    private double[] processing_omero(List<Img> images, FileWriter fw, List<String> filenames){
        //private void processing(Img<FloatType> image){


        long resultssize = 0;
        for (int i = 0; i < images.size(); i++){
            if (images.get(i).numDimensions() > 2){
                resultssize = resultssize + images.get(i).dimension(2);
            }else{
                resultssize = resultssize + 1;
            }

        }
        double[] results = new double[(int)resultssize];
        System.out.println("Opened file, processing");
        int i;
        for (i=0;i<images.size();i++) {
            String name = filenames.get(i);
            Img image = images.get(i);
            //ImageJFunctions.show(image);
            // Crops the image to get middle of the field of view

            ImagePlus input = ImageJFunctions.wrap(image, "test");
            ImageProcessor ip = input.getProcessor();
            input.trimProcessor();

            input.setProcessor(null, ip.convertToShort(false));
            input.setCalibration(calibration);
            ip.resetMinAndMax();


            input.show();
            if (input.getNChannels() > 1){
                ImagePlus[] multiinput = ChannelSplitter.split(input);
                System.out.println(multiinput.length);
                for (int j = 0; j < multiinput.length; j++){
                    ImagePlus newinput = multiinput[j];
                    //newinput.show();
                    fieldIllumination FI = new fieldIllumination(newinput);
                    double finalResult = parseResult(FI.getStringData());
                    results[i] = finalResult;
                    WriteThisFile(fw, name, j+1, finalResult);
                    i++;
                }
            }else {
                fieldIllumination FI = new fieldIllumination(input);
                double finalResult = parseResult(FI.getStringData());
                results[i] = finalResult;
                WriteThisFile(fw, name, 1, finalResult);
            }
            String[] titles = getImageTitles();
            for (String title : titles) {
                Window window = getWindow(title);
                window.dispose();
                removeWindow(window);
            }

        }
        return results;

    }






    /**
     * Parses the return string from fieldIllumination.getStringData() into the value we're interested
     * <p>
     *     Checks all 4 corners of the image for the lowest relative illumination compared to the maximum value
     *     in the image and returns that as the result.
     * </p>
     * @param stats output string from fieldIllumination
     * @return minVal double with the minimum relative intensity from all 4 corners of the image
     */
    private double parseResult(String stats){
        System.out.println(stats);
        double minVal = 1.0;
        String[] lines = stats.split("\n");

        int i;
        int init_line = 0;
        for (i=0; i< lines.length; i++){
            if (lines[i].startsWith("Top-left")){
                init_line = i;
                break;
            }
        }
        for (i=init_line;i<init_line+4;i++){
            String[] fields = lines[i].split("\t");
            double currval = Double.parseDouble(fields[2]);
            if (currval < minVal){
                minVal = currval;
            }
        }
        return minVal;
    }


    /**
     * Writes a value to an output file.
     *<p>
     * Given a FileWriter, we append the filename of the image that was processed and the results for each bead
     * processed in that image.
     *</p>
     * @param fileWriter FileWriter object for the output file
     * @param filename string with the filename of the image currently being processed
     * @param minIntensity double value with the results for the current image
     */

    private static void WriteThisFile(FileWriter fileWriter, String filename, int channel, double minIntensity){



        try {
            //Write the CSV file header


                fileWriter.append(filename);
                fileWriter.append(COMMA_DELIMITER);
                fileWriter.append(String.valueOf(channel));
                fileWriter.append(COMMA_DELIMITER);
                fileWriter.append(String.valueOf(minIntensity));
                fileWriter.append(NEW_LINE_SEPARATOR);




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
     * This main function serves for development purposes.
     * It allows you to run the plugin immediately out of
     * your integrated development environment (IDE).
     *
     * @param args whatever, it's ignored
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
        autoFOV main_class = new autoFOV();
        main_class.run();
    }
}


