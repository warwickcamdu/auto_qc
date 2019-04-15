

package uk.ac.warwick.camdu;

import Glacier2.CannotCreateSessionException;
import Glacier2.PermissionDeniedException;
import io.scif.services.DatasetIOService;
import loci.formats.in.DefaultMetadataOptions;
import loci.formats.in.MetadataLevel;
import net.imagej.ImageJ;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.RealType;
import ome.formats.OMEROMetadataStoreClient;
import ome.formats.importer.ImportCandidates;
import ome.formats.importer.ImportConfig;
import ome.formats.importer.ImportLibrary;
import ome.formats.importer.OMEROWrapper;
import ome.formats.importer.cli.ErrorHandler;
import ome.formats.importer.cli.LoggingImportMonitor;
import omero.ServerError;
import omero.api.RawFileStorePrx;
import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.facility.BrowseFacility;
import omero.gateway.facility.DataManagerFacility;
import omero.gateway.facility.RawDataFacility;
import omero.gateway.facility.TransferFacility;
import omero.gateway.model.DatasetData;
import omero.gateway.model.ExperimenterData;
import omero.gateway.model.ImageData;
import omero.log.Logger;
import omero.log.SimpleLogger;
import omero.model.*;
import omero.model.enums.ChecksumAlgorithmSHA1160;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

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


    private Gateway gateway;
    private SecurityContext ctx;
    private SimpleConnection client = new SimpleConnection();
    ExperimenterData user;

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


    private List<ImageData> connectLoadImages(){


        List<ImageData> images = new ArrayList<>();
        try {

            // Do something e.g. loading user's data.
            // Load the projects/datasets owned by the user currently logged in.
             images = client.loadImagesInDataset(dataset);


        } catch (Exception e) {
            e.printStackTrace();
        }
        return images;
    }


    private void setup_gateway() throws Exception {
        LoginCredentials cred = new LoginCredentials(username, passwd, host, port);
        Logger simpleLogger = new SimpleLogger();
        gateway = new Gateway(simpleLogger);
        user = gateway.connect(cred);
        ctx = new SecurityContext(user.getGroupId());
        client.connect(host,port,username,passwd);
    }

    private void createUI(List<ImageData> images) throws Exception {


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

        myPanel.setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        myPanel.add(new JLabel("Available files:"),c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        c.ipady = 150;
        c.gridheight = 5;
        myPanel.add(list,c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 1;
        c.gridy = 0;
        c.ipady = 0;
        c.gridheight = 1;


        myPanel.add(new JLabel("Run this routine:"),c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 1;
        c.gridy = 1;
        c.ipady = 0;
        myPanel.add(psf,c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 1;
        c.gridy = 2;
        c.ipady = 0;
        myPanel.add(coloc,c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 1;
        c.gridy = 3;
        c.ipady = 0;
        myPanel.add(fov,c);

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 1;
        c.gridy = 4;
        c.ipady = 0;
        myPanel.add(stage,c);

        //myPanel.setLayout(new BoxLayout(myPanel, BoxLayout.X_AXIS));






        int result = JOptionPane.showConfirmDialog(
                null, myPanel, "autoQC_OMERO", JOptionPane.OK_CANCEL_OPTION);


        if (result  == JOptionPane.OK_OPTION) {
            int[] selected_images = list.getSelectedIndices();
            List<ImageData> imgdatas = getSelectedImages(selected_images, images);
            List<String> filenames = getFilenames(imgdatas);
            createDirectory(outputDir);

            List<Img> imgs;

            if (psf.isSelected()){
                System.out.println("psf");
                autoPSF runpsf = new autoPSF();
                imgs = retrieveImagesPSF(imgdatas, runpsf);
                /* here is where the new UI stuff goes*/
                runpsf.setOutputDir(outputDir);
                runpsf.run_omero(imgs, imgdatas.get(0).getName(), filenames);
                saveResults(imgdatas,outputDir, "PSF");
            }
            if (coloc.isSelected()){
                System.out.println("coloc");
                autoColoc runcoloc = new autoColoc();
                imgs = retrieveImagesColoc(imgdatas, runcoloc);
                /* here is where the new UI stuff goes*/
                runcoloc.setOutputDir(outputDir);
                runcoloc.run_omero(imgs, imgdatas.get(0).getName(), filenames);
                saveResults(imgdatas,outputDir, "coloc");
            }
            if (fov.isSelected()){
                System.out.println("fov");
                autoFOV runFOV = new autoFOV();
                imgs = retrieveImagesFOV(imgdatas, runFOV);
                /* here is where the new UI stuff goes*/
                runFOV.setOutputDir(outputDir);
                runFOV.run_omero(imgs, imgdatas.get(0).getName(), filenames);
                saveResults(imgdatas,outputDir, "FOV");
            }
            if (stage.isSelected()){
                System.out.println("stage");
            }
        }




    }


    private void createDirectory(File dir){

        if (!dir.exists()) {
            dir.mkdirs();

        }
    }

    private List<String> getFilenames(List<ImageData> imgs){
        List<String> result = new ArrayList<>();
        for (ImageData img:imgs ) {
            result.add(img.getName());
        }
        return result;
    }



    private long createDataset(List<ImageData> imgs, String routine) throws DSAccessException, DSOutOfServiceException, ExecutionException {
        DataManagerFacility dm = gateway.getFacility(DataManagerFacility.class);
        BrowseFacility bw = gateway.getFacility(BrowseFacility.class);


        DatasetData original = bw.getDatasets(ctx, Arrays.asList(dataset)).iterator().next();




        String name = original.getName() + "_auto"+routine+"_results";
        System.out.println(original);
        long proj = client.get_project(original, gateway, ctx);
        System.out.println(proj);

        DatasetI ds = new DatasetI();
        ds.setName(omero.rtypes.rstring(name));
        ProjectDatasetLink link = new ProjectDatasetLinkI();
        link.setChild(ds);
        link.setParent(new ProjectI(proj, false));
        link = (ProjectDatasetLink) dm.saveAndReturnObject(ctx, link);
        //        Project test = link.getParent();
//        ds = (DatasetI) dm.saveAndReturnObject(ctx, ds);
//        link = (ProjectDatasetLink) dm.saveAndReturnObject(ctx, link);
        //System.out.println(ds.getId().getValue());
//        System.out.println(test.getId().getValue());
        return link.getChild().getId().getValue();
    }




    private void saveResults(List<ImageData> imgs, File dir, String routine) throws ExecutionException, DSAccessException, DSOutOfServiceException, ServerError {
        long dsid = createDataset(imgs, routine);
        List<String> paths = new ArrayList<>();
        List<String> csvpaths = new ArrayList<>();
        File theDir = new File(dir.getAbsolutePath()+"_"+ routine + "results");
        File[] fileNames = theDir.listFiles();

        for(File file : fileNames){
            if (file.getName().endsWith("tif")){
                System.out.println(file.getAbsolutePath());
                paths.add(file.getAbsolutePath());
            }
            if (file.getName().endsWith("csv")){
                csvpaths.add(file.getName());
            }

        }
        String[] allpaths = paths.toArray(new String[0]);
        ImportConfig config = new ome.formats.importer.ImportConfig();

        config.email.set("");
        config.sendFiles.set(true);
        config.sendReport.set(false);
        config.contOnError.set(false);
        config.debug.set(false);

        config.hostname.set(host);
        config.port.set(port);
        config.username.set(username);
        config.password.set(passwd);
        config.targetClass.set("omero.model.Dataset");
        config.targetId.set(dsid);
        System.out.println(dsid);

// the imported image will go into 'orphaned images' unless
// you specify a particular existing dataset like this:
// config.targetClass.set("omero.model.Dataset");
// config.targetId.set(1L);
        if (paths.size()>0) {
            OMEROMetadataStoreClient store;
            try {
                store = config.createStore();
                store.logVersionInfo(config.getIniVersionNumber());
                OMEROWrapper reader = new OMEROWrapper(config);
                ImportLibrary library = new ImportLibrary(store, reader);

                ErrorHandler handler = new ErrorHandler(config);
                library.addObserver(new LoggingImportMonitor());

                ImportCandidates candidates = new ImportCandidates(reader, allpaths, handler);
                reader.setMetadataOptions(new DefaultMetadataOptions(MetadataLevel.ALL));
                library.importCandidates(config, candidates);

                store.logout();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        for (String csvfile: csvpaths) {

            File csv = new File(theDir.getAbsolutePath() + File.separator + csvfile);
            String path = theDir.getAbsolutePath() + File.separator;

            int INC = 262144;
            DataManagerFacility dm = gateway.getFacility(DataManagerFacility.class);

            OriginalFile originalFile = new OriginalFileI();
            originalFile.setName(omero.rtypes.rstring(csvfile));
            originalFile.setPath(omero.rtypes.rstring(path));
            originalFile.setSize(omero.rtypes.rlong(csv.length()));
            final ChecksumAlgorithm checksumAlgorithm = new ChecksumAlgorithmI();
            checksumAlgorithm.setValue(omero.rtypes.rstring(ChecksumAlgorithmSHA1160.value));
            originalFile.setHasher(checksumAlgorithm);
            originalFile.setMimetype(omero.rtypes.rstring("application/octet-stream")); // or "application/octet-stream"
//Now we save the originalFile object
            originalFile = (OriginalFile) dm.saveAndReturnObject(ctx, originalFile);

//Initialize the service to load the raw data
            RawFileStorePrx rawFileStore = gateway.getRawFileService(ctx);

            long pos = 0;
            int rlen;
            byte[] buf = new byte[INC];
            ByteBuffer bbuf;
//Open file and read stream
            try (FileInputStream stream = new FileInputStream(csv)) {
                rawFileStore.setFileId(originalFile.getId().getValue());
                while ((rlen = stream.read(buf)) > 0) {
                    rawFileStore.write(buf, pos, rlen);
                    pos += rlen;
                    bbuf = ByteBuffer.wrap(buf);
                    bbuf.limit(rlen);
                }
                originalFile = rawFileStore.save();
            } catch (ServerError | IOException serverError) {
                serverError.printStackTrace();
            } finally {
                rawFileStore.close();
            }
//now we have an original File in DB and raw data uploaded.
//We now need to link the Original file to the image using
//the File annotation object. That's the way to do it.
            FileAnnotation fa = new FileAnnotationI();
            fa.setFile(originalFile);
            //fa.setDescription(omero.rtypes.rstring(description)); // The description set above e.g. PointsModel
            //fa.setNs(omero.rtypes.rstring(NAME_SPACE_TO_SET)); // The name space you have set to identify the file annotation.

//save the file annotation.
            fa = (FileAnnotation) dm.saveAndReturnObject(ctx, fa);

//now link the image and the annotation
            DatasetAnnotationLink link = new DatasetAnnotationLinkI();
            link.setChild(fa);
            link.setParent(new DatasetI(dsid, false));
//save the link back to the server.
            link = (DatasetAnnotationLink) dm.saveAndReturnObject(ctx, link);
// o attach to a Dataset use DatasetAnnotationLink;
        }

    }







    private List<Img> downloadImagePSF(final long imageID, autoPSF runpsf)
            throws DSOutOfServiceException, ExecutionException, DSAccessException {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(ctx, imageID);
        RawDataFacility rdf = gateway.getFacility(RawDataFacility.class);
        TransferFacility tf = gateway.getFacility(TransferFacility.class);
        tf.downloadImage(ctx,outputDir.toString(), imageID);
        String imgName = image.getName();

        return runpsf.readFile(outputDir+"/"+imgName);
    }


    private List<Img> downloadImageColoc(final long imageID, autoColoc runcoloc)
            throws DSOutOfServiceException, ExecutionException, DSAccessException {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(ctx, imageID);
        RawDataFacility rdf = gateway.getFacility(RawDataFacility.class);
        TransferFacility tf = gateway.getFacility(TransferFacility.class);
        tf.downloadImage(ctx,outputDir.toString(), imageID);
        String imgName = image.getName();

        return runcoloc.readFile(outputDir+"/"+imgName);
    }


    private List<Img> downloadImageFOV(final long imageID, autoFOV runFOV)
            throws DSOutOfServiceException, ExecutionException, DSAccessException {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(ctx, imageID);
        RawDataFacility rdf = gateway.getFacility(RawDataFacility.class);
        TransferFacility tf = gateway.getFacility(TransferFacility.class);
        tf.downloadImage(ctx,outputDir.toString(), imageID);
        String imgName = image.getName();

        return runFOV.readFile(outputDir+"/"+imgName);
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


    private List<Img> retrieveImagesPSF(List<ImageData> imgs, autoPSF runpsf) throws URISyntaxException, CannotCreateSessionException, PermissionDeniedException, ServerError, IOException, ExecutionException, DSAccessException, DSOutOfServiceException {
        List<Img> result = new ArrayList<>();

        for (ImageData img:imgs){
            result.add(downloadImagePSF(img.getId(), runpsf).get(0));
        }

    return result;
    }


    private List<Img> retrieveImagesColoc(List<ImageData> imgs, autoColoc runcoloc) throws CannotCreateSessionException, PermissionDeniedException, ServerError, IOException, ExecutionException, DSAccessException, DSOutOfServiceException {
        List<Img> result = new ArrayList<>();

        for (ImageData img:imgs){
            result.add(downloadImageColoc(img.getId(), runcoloc).get(0));
        }

        return result;
    }

    private List<Img> retrieveImagesFOV(List<ImageData> imgs, autoFOV runFOV) throws CannotCreateSessionException, PermissionDeniedException, ServerError, IOException, ExecutionException, DSAccessException, DSOutOfServiceException {
        List<Img> result = new ArrayList<>();

        for (ImageData img:imgs){
            result.add(downloadImageFOV(img.getId(), runFOV).get(0));
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
        try {
            setup_gateway();
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<ImageData> images = null;

        images = connectLoadImages();

        try {
            createUI(images);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    gateway.disconnect();
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


