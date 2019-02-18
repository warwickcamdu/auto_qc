package uk.ac.warwick.camdu;

/*
 * To the extent possible under law, the ImageJ developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */


import fiji.plugin.trackmate.detection.LogDetectorFactory;
import fiji.plugin.trackmate.tracking.SpotTracker;
import fiji.plugin.trackmate.util.LogRecorder;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.ChannelSplitter;
import ij.process.FloatPolygon;
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
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

import fiji.plugin.trackmate.*;
import fiji.plugin.trackmate.providers.DetectorProvider;
import fiji.plugin.trackmate.providers.EdgeAnalyzerProvider;
import fiji.plugin.trackmate.providers.SpotAnalyzerProvider;
import fiji.plugin.trackmate.providers.TrackAnalyzerProvider;
import fiji.plugin.trackmate.providers.TrackerProvider;
import fiji.plugin.trackmate.tracking.SpotTrackerFactory;
import fiji.plugin.trackmate.tracking.sparselap.SimpleSparseLAPTrackerFactory;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_SPLITTING;
import static fiji.plugin.trackmate.tracking.TrackerKeys.KEY_ALLOW_TRACK_MERGING;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_DO_SUBPIXEL_LOCALIZATION;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_RADIUS;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_THRESHOLD;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_TARGET_CHANNEL;
import static fiji.plugin.trackmate.detection.DetectorKeys.KEY_DO_MEDIAN_FILTERING;
import static java.lang.Math.abs;


/**
 */

@Plugin(type = Command.class, menuPath = "Plugins>autoQC>autoStageRepro")
public class autoStageRepro<T extends RealType<T>> extends Component implements Command {

    @Parameter
    private ImageJ ij;



    String ext = ".dv";
    int beads = 3;
    int minSeparation = 15;
    Calibration calibration;
    static String COMMA_DELIMITER = ",";
    static String NEW_LINE_SEPARATOR = "\n";

    String srcDir = "";

    public void setExtension(String extension){
        ext = extension;

    }

    public void setBeads(int beadnum){
        beads = beadnum;

    }

    public void setMinSep(int minsep){
        minSeparation = minsep;

    }

    public void setDir(String sourceDir){
        srcDir = sourceDir;

    }



    public FileWriter printOutputHeader(String FilePath){
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(FilePath);
            //Write the CSV file header
            fileWriter.append("file_id"+COMMA_DELIMITER+"bead_id"+COMMA_DELIMITER+"max_x_displacement"+COMMA_DELIMITER+"max_y_displacement");
            //Add a new line separator after the header
            fileWriter.append(NEW_LINE_SEPARATOR);

        } catch (Exception e) {

            System.out.println("Error in CsvFileWriter !!!");
            e.printStackTrace();

        }

        return fileWriter;
    }

    public void createUI(){
        JTextField extField = new JTextField(".dv",10);
        JTextField beadField = new JTextField("1",5);
        JTextField sepField = new JTextField("30",5);

        JButton browseBtn = new JButton("Browse:");

        browseBtn.addActionListener(e -> browseButtonActionPerformed(e));



        System.out.println(srcDir);



        JPanel myPanel = new JPanel();

        myPanel.add(new JLabel("File extension:"));
        myPanel.add(extField);

        myPanel.add(new JLabel("Number of beads:"));
        myPanel.add(beadField);

        myPanel.add(new JLabel("Minimum bead separation (px):"));
        myPanel.add(sepField);

        myPanel.add(new JLabel("Please select your dataset:"));
        myPanel.add(browseBtn);

        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

        int result = JOptionPane.showConfirmDialog(
                null, myPanel, "autoColoc", JOptionPane.OK_CANCEL_OPTION);

        setExtension(extField.getText());
        setBeads(Integer.parseInt(beadField.getText()));
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
        String resultPath = selectedDir + File.separator + "summary_stagerepro.csv";
        FileWriter fw = printOutputHeader(resultPath);

        for (final File fileEntry : Objects.requireNonNull(selectedDir.listFiles())){

            if (fileEntry.getName().endsWith(ext)&&fileEntry.getName().contains("stage")){

                System.out.println("Processing file: " + fileEntry.getName());
                String path = selectedDir + File.separator + fileEntry.getName();

                currentFile = readFile(path, selectedDir);

                double[][] finalResult = processing(currentFile, path);



                WriteFile(fw, resultPath,fileEntry.getName(),finalResult);

            }




        }

        CloseFile(fw);
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



    private double[] track(ImagePlus imp){

        System.out.println(imp.getType());

        double[] max = new double[2];
        max[0] = 0;
        max[1] = 0;
        imp.setOpenAsHyperStack( true );
        imp.show();

        final DetectorProvider detectorProvider = new DetectorProvider();
        final TrackerProvider trackerProvider = new TrackerProvider();
        final SpotAnalyzerProvider spotAnalyzerProvider = new SpotAnalyzerProvider();
        final EdgeAnalyzerProvider edgeAnalyzerProvider = new EdgeAnalyzerProvider();
        final TrackAnalyzerProvider trackAnalyzerProvider = new TrackAnalyzerProvider();
        final Model model = new Model();

        final SpotTrackerFactory tf = new SimpleSparseLAPTrackerFactory();
        final LogDetectorFactory detectorFactory =  new LogDetectorFactory();
        final Map< String, Object > ts = tf.getDefaultSettings();
        final Map< String, Object > logs = detectorFactory.getDefaultSettings();

        Logger logger = new LogRecorder( Logger.DEFAULT_LOGGER );
        model.setLogger(logger);




        final Settings settings = new Settings();



        settings.setFrom(imp);
        ts.put( KEY_ALLOW_TRACK_SPLITTING, true );
        ts.put( KEY_ALLOW_TRACK_MERGING, true );

        logs.put(KEY_DO_MEDIAN_FILTERING,false);
        logs.put(KEY_DO_SUBPIXEL_LOCALIZATION,true);
        logs.put(KEY_RADIUS, 3.0);
        logs.put(KEY_TARGET_CHANNEL,1);
        logs.put(KEY_THRESHOLD,1.0);

        settings.trackerFactory = tf;
        settings.trackerSettings = ts;
        settings.detectorFactory = detectorFactory;
        settings.detectorSettings = logs;

        final String spaceUnits = settings.imp.getCalibration().getXUnit();
        final String timeUnits = settings.imp.getCalibration().getTimeUnit();
        model.setPhysicalUnits( spaceUnits, timeUnits );


        // Re-run TrackMate for the tracking part
        final TrackMate trackmate = new TrackMate( model, settings );
        boolean trackingOk = trackmate.execDetection();
        trackmate.getModel().getSpots().setVisible(true);

        trackingOk = trackmate.execTracking();
        System.out.println(trackingOk);
        double min_x = 0;
        double min_y = 0;

        Set<Integer> ids = model.getTrackModel().trackIDs(true);
        System.out.println("number of tracks:"+ids.size());
        for (Integer id: ids){
            Set<Spot> spots = model.getTrackModel().trackSpots(id);
            for (Spot spot: spots) {
                System.out.println(spot.getFeature("FRAME") + " " + spot.getFeature("POSITION_X") + " " + spot.getFeature("POSITION_Y") + " " + max[0] + " " + max[1]);
                Double t = spot.getFeature("FRAME");
                if (t == 0.0) {
                    min_x = spot.getFeature("POSITION_X");
                    min_y = spot.getFeature("POSITION_Y");
                }
            }
            for (Spot spot: spots) {
                Double t = spot.getFeature("FRAME");
                if (t != 0.0) {
                    if (abs(min_x - spot.getFeature("POSITION_X")) > max[0]){
                        max[0] = abs(min_x - spot.getFeature("POSITION_X"));
                    }
                    if (abs(min_y - spot.getFeature("POSITION_Y")) > max[1]){
                        max[1] = abs(min_y - spot.getFeature("POSITION_Y"));
                    }
                }

            }
        }

        return max;
    }

    public double[][] processing(Img image, String path){
        //private void processing(Img<FloatType> image){


        IJ.run("Set Measurements...", "min centroid integrated redirect=None decimal=3");
        System.out.println("Opened file, processing");

        //ImageJFunctions.show(image);
        // Crops the image to get middle of the field of view

        FinalInterval interval = FinalInterval.createMinSize(0,0,0,image.dimension(0),image.dimension(1),image.dimension(2));
//        if (image.dimension(0) > 300 && image.dimension(1) > 300){
//            System.out.println(image.dimension(0));
//            interval = FinalInterval.createMinSize(image.dimension(0)/2-150,image.dimension(1)/2-150,0,300,300,image.dimension(2));
//        }


        RandomAccessibleInterval cropped;
        cropped  = ij.op().transform().crop(image,interval, true);
//        ImageJFunctions.show(cropped);

        interval = FinalInterval.createMinSize(0,0,0,cropped.dimension(0),cropped.dimension(1),1);
        RandomAccessibleInterval finalcrop;
        finalcrop  = ij.op().transform().crop(cropped,interval, true);
        ImageJFunctions.show(finalcrop);

        // detect beads and measure for intensity and x/y coords
        IJ.run("Find Maxima...", "noise=1000 output=[Point Selection] exclude");
        ImagePlus imp = IJ.getImage();
        IJ.saveAsTiff(imp,path+"_beads"+File.separator+"allbeads"+".tif");
        // Gets coordinates of ROIs

        Roi test = imp.getRoi();
        FloatPolygon floatPolygon = test.getFloatPolygon();

        float[][] resultsTable = new float[floatPolygon.npoints][3];

        // loops over ROIs and get pixel Vvlue at their coordinate.
        for (int i=1; i < floatPolygon.npoints; i++){

            float intx = floatPolygon.xpoints[i];
            float inty = floatPolygon.ypoints[i];

            final RandomAccess<FloatType> r = cropped.randomAccess();
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
                Roi beadROI  = new Roi(resultsTable[firstPosition][0]-15,resultsTable[firstPosition][1]-15,30,30);
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

        IJ.saveAsTiff(imp,path+"_beads"+File.separator+"allbeads"+".tif");


        double[][] finalResults  = new double[beads][4];





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




        //ij.ui().showUI();
        // loops over selected pixels and crops out the PSFs
        for (int i = 0; i < goodX.length; i++){

            interval = FinalInterval.createMinSize(0,0,0,20,20,cropped.dimension(2));

            if (goodX[i]>15 && goodY[i]>15){
                interval = FinalInterval.createMinSize((int)goodX[i]-15,(int)goodY[i]-15,0,30,30,cropped.dimension(2));
            }

            RandomAccessibleInterval newcropped  = ij.op().transform().crop(cropped,interval, true);
            ImagePlus IPcropped = ImageJFunctions.wrapFloat(newcropped, "test");
            IPcropped.setDimensions(1, 1, (int) newcropped.dimension(2));
            IPcropped.setOpenAsHyperStack(true);
            ImageProcessor ip = IPcropped.getProcessor();
            ip.resetMinAndMax();

            IPcropped.setCalibration(calibration);

            FileSaver fs = new FileSaver(IPcropped);
            fs.saveAsTiff(path+"_beads"+File.separator+"bead_"+i+".tif");

            // crops stack around the specified coordinates

            // calls GetRes to extract the resolution form the PSFs


            double[] maxShift = track(IPcropped);


            // multiply by the correction factor
            /*double xRes = qcMetrics[0] * corr_factor_x;
            double yRes = qcMetrics[1] * corr_factor_y;
            double zRes = qcMetrics[2] * corr_factor_z;*/
            finalResults[i][0] = i;
            finalResults[i][1] = maxShift[0];
            finalResults[i][2] = maxShift[1];


        }

        WindowManager.closeAllWindows();
        return finalResults;

    }


    public static boolean WriteFile(FileWriter fileWriter,String FilePath, String filename, double[][] BeatResArray){


        try {

            //Write the CSV file header
            //Add a new line separator after the header
            int Datalength = BeatResArray.length;
            for (int x = 0; x <Datalength; x++)
            {
                fileWriter.append(filename);
                fileWriter.append(COMMA_DELIMITER);
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

        }

        return true;

    }

    public void CloseFile(FileWriter fileWriter){
        try {

            assert fileWriter != null;
            fileWriter.flush();
            fileWriter.close();

        } catch (IOException e) {

            System.out.println("Error while flushing/closing fileWriter !!!");
            e.printStackTrace();

        }
    }



    public static double[] GetCoal(ImagePlus beadStack[]){
        microscope[] micro = new microscope[4];
        int i;
        for (i=0;i<4;i++){
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
        autoStageRepro main_class = new autoStageRepro();
        main_class.run();
    }
}



