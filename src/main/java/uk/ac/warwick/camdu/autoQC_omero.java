

package uk.ac.warwick.camdu;

import Glacier2.CannotCreateSessionException;
import Glacier2.PermissionDeniedException;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.filter.MaximumFinder;
import ij.process.ImageProcessor;
import io.scif.config.SCIFIOConfig;
import io.scif.img.ImgOpener;
import io.scif.services.DatasetIOService;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImportProcess;
import loci.plugins.in.ImporterOptions;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.omero.OMEROLocation;
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
import omero.ServerError;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.facility.RawDataFacility;
import omero.gateway.facility.TransferFacility;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.log.Logger;
import omero.log.SimpleLogger;
import org.scijava.Context;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static ij.WindowManager.*;
import static java.lang.Math.ceil;
import static java.lang.Math.round;

/**
 *
 * autoQC_omero - Running autoQC routines on files from OMERO
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

@Plugin(type = Command.class, menuPath = "Plugins>autoQC>autoQC_omero")
public class autoQC_omero<T extends RealType<T>> extends Component implements Command {


    private static final String COMMA_DELIMITER = ",";
    private static final String NEW_LINE_SEPARATOR = "\n";

    /**
     * All parameters are the user-defined inputs from Fiji
     */

    @Parameter
    private ImageJ ij;

    @Parameter
    private DatasetIOService datasetIOService;


    /**
     * beads : integer, number of beads to be processed per file
     */
    @Parameter(label = "hostname:")
    private String host =  "";
    /**
     * corr_factor_x/y/z : doubles, correction factor from FWHM resolution values (in case different definition of
     *                     resolution, or wrong metadata, etc etc)
     */
    @Parameter(label = "port:")
    private int port = 4064;
    /**
     * corr_factor_x/y/z : doubles, correction factor from FWHM resolution values (in case different definition of
     *                      resolution, or wrong metadata, etc etc)
     */
    @Parameter(label = "username:")
    private String username = "";
    /**
     * corr_factor_x/y/z : doubles, correction factor from FWHM resolution values (in case different definition of
     *                     resolution, or wrong metadata, etc etc)
     */
    @Parameter(label = "password:", style = "password")
    private String passwd = "";

    @Parameter(label = "Dataset ID:")
    private long dataset;

    @Parameter(label = "Output folder:", style = "directory")
    private File outputDir;

    SecurityContext ctx;

    private List<ImageData> connectLoadImages() throws CannotCreateSessionException, PermissionDeniedException, ServerError {
        SimpleConnection client = new SimpleConnection();

        List<ImageData> images = new ArrayList<>();
        try {
            ctx = client.connect(host,port,username,passwd);
            // Do something e.g. loading user's data.
            // Load the projects/datasets owned by the user currently logged in.
             images = client.loadImagesInDataset(dataset);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return images;
    }


    private void createUI(List<ImageData> images, SecurityContext ctx) throws IOException, ServerError, CannotCreateSessionException, PermissionDeniedException, URISyntaxException, ExecutionException, DSAccessException, DSOutOfServiceException {


        DefaultListModel<String> l1 = new DefaultListModel<>();

        for (ImageData img: images){
            l1.addElement(img.getName());
        }


        JList<String> list = new JList<>(l1);

        JRadioButton psf = new JRadioButton("autoPSF");
        JRadioButton coloc = new JRadioButton("autoColoc");
        JRadioButton fov = new JRadioButton("autoFOV");
        JRadioButton stage = new JRadioButton("autoStageRepro");


        JPanel myPanel = new JPanel();

        ButtonGroup bg = new ButtonGroup();
        bg.add(psf);
        bg.add(coloc);
        bg.add(fov);
        bg.add(stage);

        myPanel.add(new JLabel("Available files:"));
        myPanel.add(list);

        myPanel.add(new JLabel("Run this routine:"));
        myPanel.add(psf);
        myPanel.add(coloc);
        myPanel.add(fov);
        myPanel.add(stage);

        myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.Y_AXIS));

        int result = JOptionPane.showConfirmDialog(
                null, myPanel, "autoQC_OMERO", JOptionPane.OK_CANCEL_OPTION);


        if (result  == JOptionPane.OK_OPTION) {
            int[] selected_images = list.getSelectedIndices();
            List<ImageData> imgdatas = getSelectedImages(selected_images, images);

            List<Img> imgs;

            if (psf.isSelected()){
                System.out.println("psf");
                autoPSF<T> runpsf = new autoPSF();
                imgs = retrieveImages(imgdatas, runpsf);
                /* here is where the new UI stuff goes*/
                runpsf.setOutputDir(outputDir);
                runpsf.run_omero(imgs, imgdatas.get(0).getName());
            }
            if (coloc.isSelected()){
                System.out.println("coloc");
            }
            if (fov.isSelected()){
                System.out.println("fov");
            }
            if (stage.isSelected()){
                System.out.println("stage");
            }
        }




    }


    public List<Img> downloadImage(final omero.client client, final long imageID, autoPSF runpsf)
            throws omero.ServerError, IOException, DSOutOfServiceException, ExecutionException, DSAccessException {
        LoginCredentials cred = new LoginCredentials(username, passwd, host, port);
        Logger simpleLogger = new SimpleLogger();
        Gateway gateway = new Gateway(simpleLogger);
        ExperimenterData user = gateway.connect(cred);
        SecurityContext ctx = new SecurityContext(user.getGroupId());
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(ctx, imageID);
        RawDataFacility rdf = gateway.getFacility(RawDataFacility.class);
        TransferFacility tf = gateway.getFacility(TransferFacility.class);
        tf.downloadImage(ctx,outputDir.toString(), imageID);
        String imgName = image.getName();
        gateway.disconnect();

        return runpsf.readFile(outputDir+"/"+imgName);
    }


    private static String credentials(final omero.client client) {
        return "server=" + getHost(client) + //
                "&port=" + client.getProperty("omero.port") + //
                "&sessionID=" + client.getSessionId();
    }


    private static String getHost(final omero.client client) {
        String host = client.getProperty("omero.host");
        if (host == null || host.isEmpty()) {
            final String router = client.getProperty("Ice.Default.Router");
            final int index = router.indexOf("-h ");
            if (index == -1) throw new IllegalArgumentException("hostname required");
            host = router.substring(index + 3, router.length());
        }
        return host;
    }


    private List<Img> retrieveImages(List<ImageData> imgs, autoPSF runpsf) throws URISyntaxException, CannotCreateSessionException, PermissionDeniedException, ServerError, IOException, ExecutionException, DSAccessException, DSOutOfServiceException {
        List<Img> result = new ArrayList<>();
        omero.client b = new omero.client(host, port);
        b.createSession(username, passwd);
        for (ImageData img:imgs){
            result.add(downloadImage(b, img.getId(), runpsf).get(0));
        }

    return result;
    }

    
    private List<ImageData> getSelectedImages(int[] selections, List<ImageData> images){

        List<ImageData> result = new ArrayList<ImageData>();
        for (int i:selections){
            result.add(images.get(i));
        }
        return result;
    }
    

    @Override
    public void run() {


//        createUI();

        ij = new net.imagej.ImageJ();
        List<ImageData> images = null;
        try {
            images = connectLoadImages();
        } catch (CannotCreateSessionException e) {
            e.printStackTrace();
        } catch (PermissionDeniedException e) {
            e.printStackTrace();
        } catch (ServerError serverError) {
            serverError.printStackTrace();
        }
        try {
            createUI(images, ctx);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ServerError serverError) {
            serverError.printStackTrace();
        } catch (PermissionDeniedException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (CannotCreateSessionException e) {
            e.printStackTrace();
        } catch (DSOutOfServiceException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        } catch (DSAccessException e) {
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
        autoQC_omero main_class = new autoQC_omero();
        main_class.run();
        }
    }


