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
import ij.measure.Calibration;
import ij.process.ImageProcessor;
import loci.formats.FormatException;
import loci.plugins.BF;
import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.FileWidget;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Objects;

import static ij.WindowManager.*;


/**
 */

@SuppressWarnings("unchecked")
@Plugin(type = Command.class, menuPath = "Plugins>autoQC>autoFOV")
public class autoFOV<T extends RealType<T>> extends Component implements Command {

    private static final String COMMA_DELIMITER = ",";
    private static final String NEW_LINE_SEPARATOR = "\n";

    @Parameter
    private ImageJ ij;

    @Parameter(label = "File extension:")
    private String ext = ".tif";

    @Parameter(style="files", label = "select files:")
    private File[] srcDir;

    private Calibration calibration;


    private void setExtension(String extension){
        ext = extension;

    }



    /*private void setDir(String sourceDir){
        srcDir = new File(sourceDir);

    }*/


    private FileWriter printOutputHeader(String FilePath){
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(FilePath);
            //Write the CSV file header
            fileWriter.append("file_id"+COMMA_DELIMITER+"minimum_relative_intensity");
            //Add a new line separator after the header
            fileWriter.append(NEW_LINE_SEPARATOR);

        } catch (Exception e) {

            System.out.println("Error in CsvFileWriter !!!");
            e.printStackTrace();

        }

        return fileWriter;
    }




    private void createUI(){
        JTextField extField = new JTextField(".tif",10);


        JButton browseBtn = new JButton("Browse:");

        browseBtn.addActionListener(e -> browseButtonActionPerformed());



        System.out.println(srcDir);



        JPanel myPanel = new JPanel();

        myPanel.add(new JLabel("File extension:"));
        myPanel.add(extField);


        myPanel.add(new JLabel("Please select your datset:"));
        myPanel.add(browseBtn);

        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

        JOptionPane.showConfirmDialog(
                null, myPanel, "autoFOV", JOptionPane.OK_CANCEL_OPTION);

        setExtension(extField.getText());




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


       // setDir(sourceDir);
    }

    @Override
    public void run() {


//        createUI();

        ImageJ ij = new ImageJ();
        //String srcDir = selectedDir.getAbsolutePath();

        System.out.println(srcDir);



//        File selectedDir = new File(srcDir);
        //File selectedDir = srcDir;
        Img<FloatType> currentFile;


        String selectedDir = srcDir[0].getParent();
        String resultPath = selectedDir + File.separator + "summary_coloc.csv";
        FileWriter fw = printOutputHeader(resultPath);


        for (final File fileEntry : Objects.requireNonNull(srcDir)){

            if (fileEntry.getName().endsWith(ext)&&fileEntry.getName().contains("psf")){

                System.out.println("Processing file: " + fileEntry.getName());
                String path = fileEntry.getPath();

                currentFile = readFile(path);

                double finalResult = processing(currentFile);



                WriteFile(fw,fileEntry.getName(),finalResult);

            }




        }


    


        // skip irrelevant filenames, do stuff for relevant ones

        CloseFile(fw);
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

            imgFinal = ImageJFunctions.convertFloat(imps[0]);
            // for (ImagePlus imp : imps) imp.show();
            // We don't need to show them

        } catch (FormatException | IOException exc) {

            IJ.error("Sorry, an error occurred: " + exc.getMessage());

        }

        return imgFinal;




    }

    private double processing(Img image){
        //private void processing(Img<FloatType> image){



        System.out.println("Opened file, processing");

        //ImageJFunctions.show(image);
        // Crops the image to get middle of the field of view

        ImagePlus input = ImageJFunctions.wrap(image,"test");
        ImageProcessor ip = input.getProcessor();
        input.trimProcessor();

        input.setProcessor(null, ip.convertToShort(false));
        input.setCalibration(calibration);
        ip.resetMinAndMax();



        input.show();
        fieldIllumination FI = new fieldIllumination(input);
        double finalResult = parseResult(FI.getStringData());

        String[] titles = getImageTitles();
        for (String title:titles){
            Window window = getWindow(title);
            window.dispose();
            removeWindow(window);
        }
        return finalResult;

    }


    private double parseResult(String stats){
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


    private static void WriteFile(FileWriter fileWriter, String filename, double minIntensity){



        try {
            //Write the CSV file header
            fileWriter.append(filename);
            fileWriter.append(COMMA_DELIMITER);
            fileWriter.append(String.valueOf(minIntensity));
               fileWriter.append(NEW_LINE_SEPARATOR);


        } catch (Exception e) {

            System.out.println("Error in CsvFileWriter !!!");
            e.printStackTrace();

        }

    }



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


