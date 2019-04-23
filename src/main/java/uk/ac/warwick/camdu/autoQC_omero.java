

package uk.ac.warwick.camdu;

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
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 *
 * autoQC_omero - Running autoQC routines on files from OMERO
 *<p>
 * This class implements all the other routines from this plugin, but from images stored in an OMERO server.
 * It retrieves the images, does the necessary processing and then saves eventual output images (bead crops, etc)
 * back to a new OMERO dataset, together with the output CSV files (that are attached to the dataset).
 *</p>
 * @param <T> I don't think we actually use this
 * @author Erick Martins Ratamero
 * @version 1.0
 */

@Plugin(type = Command.class, menuPath = "Plugins>autoQC>autoQC_omero")
public class autoQC_omero<T extends RealType<T>> extends Component implements Command {


    /**
    Using OMERO requires a few extra things - we declare those here
     */

    private Gateway gateway;
    private SecurityContext ctx;
    private SimpleConnection client = new SimpleConnection();
    ExperimenterData user;
    private List<File> files;
    private List<File> tempfiles;

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

    /**
     * Simple wrapper for loadImagesInDataset
     *<p>
     * This function uses the class-wide variable dataset and the class-wide persistent OMERO client (client)
     * and wraps loadImagesInDataset with a try-catch. It then returns the list of ImageDatas in case it is
     * successful.
     *</p>

     * @return images List of ImageDatas - contains the ImageData objects from the dataset
     *
     */
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



    /**
     * Simple wrapper for creating an OMERO client
     *<p>
     * This function uses OMERO functions to setup the class-wide OMERO client and security context.
     * It creates login credentials, connects the gateway and sets up the security context on the user's relevant
     * group, plus connects the OMERO client.
     *</p>

     */

    private void setup_gateway() throws Exception {
        LoginCredentials cred = new LoginCredentials(username, passwd, host, port);
        Logger simpleLogger = new SimpleLogger();
        gateway = new Gateway(simpleLogger);
        user = gateway.connect(cred);
        ctx = new SecurityContext(user.getGroupId());
        client.connect(host,port,username,passwd);
    }



    /**
     * Creates UI for selecting files and analysis routine
     *<p>
     * This function creates a UI window with a list of files contained in the dataset, radio buttons for selecting
     * an analysis routine and a checkbox for choosing whether to keep temp files or not.
     *</p>

     @param images List of ImageDatas with all images contained in the desired dataset
     */

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
        JCheckBox keep = new JCheckBox("Keep temporary files ");


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

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 1;
        c.gridy = 5;
        c.ipady = 0;
        myPanel.add(keep,c);

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
                runpsf.run_omero(imgs, filenames);
                saveResults(outputDir, "PSF");
            }
            if (coloc.isSelected()){
                System.out.println("coloc");
                autoColoc runcoloc = new autoColoc();
                imgs = retrieveImagesColoc(imgdatas, runcoloc);
                /* here is where the new UI stuff goes*/
                runcoloc.setOutputDir(outputDir);
                runcoloc.run_omero(imgs, filenames);
                saveResults(outputDir, "coloc");
            }
            if (fov.isSelected()){
                System.out.println("fov");
                autoFOV runFOV = new autoFOV();
                imgs = retrieveImagesFOV(imgdatas, runFOV);
                /* here is where the new UI stuff goes*/
                runFOV.setOutputDir(outputDir);
                runFOV.run_omero(imgs, filenames);
                saveResults(outputDir, "FOV");
            }
            if (stage.isSelected()){
                System.out.println("stage");
                autoStageRepro runStage = new autoStageRepro();
                imgs = retrieveImagesStage(imgdatas, runStage);
                /* here is where the new UI stuff goes*/
                runStage.setOutputDir(outputDir);
                runStage.run_omero(imgs,  filenames);
                saveResults(outputDir, "stage");
            }
        }

        if (!keep.isSelected()){

            for(File f: files){
                f.delete();
            }
                 }




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
     * Simple wrapper for getting a list of files from a list of ImageDatas
     *<p>
     * Simple for loop over the input list of ImageDatas that creates a list of Strings with the filenames.
     *</p>

     @param imgs List of ImageDatas with the relevant images
     @return result List of Strings with the image names
     */

    private List<String> getFilenames(List<ImageData> imgs){
        List<String> result = new ArrayList<>();
        for (ImageData img:imgs ) {
            result.add(img.getName());
        }
        return result;
    }




    /**
     * Creates a new OMERO dataset based on the original
     *<p>
     * Uses the class-wide dataset variable and OMERO client/context to retrieve the name of the original dataset
     * and create a new one to store the results of the current analysis routine.
     *</p>

     @param routine String with the name of the current analysis routine
     @return value Dataset ID for the created dataset
     */

    private long createDataset(String routine) throws DSAccessException, DSOutOfServiceException, ExecutionException {
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

    /**
     * Saves the analysis results back to OMERO
     *<p>
     * This function goes through the local directory where outputs were saved, uploads/imports all output TIFs to
     * the OMERO dataset created for outputs and then attaches the CSV files with analysis results to it.
     *</p>

     @param dir File pointing to the local directory containing outputs
     @param routine String with the name of the current analysis routine
     */


    private void saveResults(File dir, String routine) throws ExecutionException, DSAccessException, DSOutOfServiceException, ServerError {
        long dsid = createDataset(routine);
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
            /*
            fa.setDescription(omero.rtypes.rstring(description)); // The description set above e.g. PointsModel
            fa.setNs(omero.rtypes.rstring(NAME_SPACE_TO_SET)); // The name space you have set to identify the file annotation.
            save the file annotation.
            */

            fa = (FileAnnotation) dm.saveAndReturnObject(ctx, fa);

//now link the image and the annotation
            DatasetAnnotationLink link = new DatasetAnnotationLinkI();
            link.setChild(fa);
            link.setParent(new DatasetI(dsid, false));
//save the link back to the server.
            dm.saveAndReturnObject(ctx, link);
// o attach to a Dataset use DatasetAnnotationLink;
        }

    }





    /**
     * Downloads selected image to a temp directory
     *<p>
     * This function uses the existing OMERO gateway to download the image given by its ID. It then uses the
     * readFile function from the analysis class to import the downloaded image as a List of Img objects.
     *</p>

     @param imageID OMERO ID of the desired image
     @param runpsf autoPSF instance that will be used to read files
     @return file List of Img objects with all images contained in the download file
     */

    private List<Img> downloadImagePSF(final long imageID, autoPSF runpsf)
            throws DSOutOfServiceException, ExecutionException, DSAccessException {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(ctx, imageID);
        TransferFacility tf = gateway.getFacility(TransferFacility.class);
        tempfiles = tf.downloadImage(ctx,outputDir.toString(), imageID);
        String imgName = image.getName();

        return runpsf.readFile(outputDir+"/"+imgName);
    }




    /**
     * Downloads selected image to a temp directory
     *<p>
     * This function uses the existing OMERO gateway to download the image given by its ID. It then uses the
     * readFile function from the analysis class to import the downloaded image as a List of Img objects.
     *</p>

     @param imageID OMERO ID of the desired image
     @param runcoloc autoColoc instance that will be used to read files
     @return file List of Img objects with all images contained in the download file
     */

    private List<Img> downloadImageColoc(final long imageID, autoColoc runcoloc)
            throws DSOutOfServiceException, ExecutionException, DSAccessException {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(ctx, imageID);
        TransferFacility tf = gateway.getFacility(TransferFacility.class);
        tempfiles = tf.downloadImage(ctx,outputDir.toString(), imageID);
        String imgName = image.getName();

        return runcoloc.readFile(outputDir+"/"+imgName);
    }



    /**
     * Downloads selected image to a temp directory
     *<p>
     * This function uses the existing OMERO gateway to download the image given by its ID. It then uses the
     * readFile function from the analysis class to import the downloaded image as a List of Img objects.
     *</p>

     @param imageID OMERO ID of the desired image
     @param runFOV autoFOV instance that will be used to read files
     @return file List of Img objects with all images contained in the download file
     */

    private List<Img> downloadImageFOV(final long imageID, autoFOV runFOV)
            throws DSOutOfServiceException, ExecutionException, DSAccessException {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(ctx, imageID);
        TransferFacility tf = gateway.getFacility(TransferFacility.class);
        tempfiles = tf.downloadImage(ctx,outputDir.toString(), imageID);
        String imgName = image.getName();

        return runFOV.readFile(outputDir+"/"+imgName);
    }




    /**
     * Downloads selected image to a temp directory
     *<p>
     * This function uses the existing OMERO gateway to download the image given by its ID. It then uses the
     * readFile function from the analysis class to import the downloaded image as a List of Img objects.
     *</p>

     @param imageID OMERO ID of the desired image
     @param runStage autoStageRepro instance that will be used to read files
     @return file List of Img objects with all images contained in the download file
     */

    private List<Img> downloadImageStage(final long imageID, autoStageRepro runStage)
            throws DSOutOfServiceException, ExecutionException, DSAccessException {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(ctx, imageID);
        TransferFacility tf = gateway.getFacility(TransferFacility.class);
        tempfiles = tf.downloadImage(ctx,outputDir.toString(), imageID);
        String imgName = image.getName();

        return runStage.readFile(outputDir+"/"+imgName);
    }






    /**
     * Retrieves all selected images from the input list
     *<p>
     * This function wraps the internal downloadImage function to do so for all selected input files, given as a list
     * of ImageDatas. It returns a list of Img objects containing all images on all selected files.
     *</p>

     @param imgs List of ImageDatas containing the selected files given by the user
     @param runpsf autoPSF instance that will be used to read files
     @return file List of Img objects with all images contained in the downloaded files
     */


    private List<Img> retrieveImagesPSF(List<ImageData> imgs, autoPSF runpsf) throws ExecutionException, DSAccessException, DSOutOfServiceException {
        List<Img> result = new ArrayList<>();
        files = new ArrayList<>();
        for (ImageData img:imgs){
            result.add(downloadImagePSF(img.getId(), runpsf).get(0));
            files.addAll(tempfiles);
        }

    return result;
    }



    /**
     * Retrieves all selected images from the input list
     *<p>
     * This function wraps the internal downloadImage function to do so for all selected input files, given as a list
     * of ImageDatas. It returns a list of Img objects containing all images on all selected files.
     *</p>

     @param imgs List of ImageDatas containing the selected files given by the user
     @param runcoloc autoColoc instance that will be used to read files
     @return file List of Img objects with all images contained in the downloaded files
     */

    private List<Img> retrieveImagesColoc(List<ImageData> imgs, autoColoc runcoloc) throws ExecutionException, DSAccessException, DSOutOfServiceException {
        List<Img> result = new ArrayList<>();
        files = new ArrayList<>();
        for (ImageData img:imgs){
            result.add(downloadImageColoc(img.getId(), runcoloc).get(0));
            files.addAll(tempfiles);
        }

        return result;
    }



    /**
     * Retrieves all selected images from the input list
     *<p>
     * This function wraps the internal downloadImage function to do so for all selected input files, given as a list
     * of ImageDatas. It returns a list of Img objects containing all images on all selected files.
     *</p>

     @param imgs List of ImageDatas containing the selected files given by the user
     @param runFOV autoFOV instance that will be used to read files
     @return file List of Img objects with all images contained in the downloaded files
     */

    private List<Img> retrieveImagesFOV(List<ImageData> imgs, autoFOV runFOV) throws ExecutionException, DSAccessException, DSOutOfServiceException {
        List<Img> result = new ArrayList<>();
        files = new ArrayList<>();
        for (ImageData img:imgs){
            result.add(downloadImageFOV(img.getId(), runFOV).get(0));
            files.addAll(tempfiles);
        }

        return result;
    }



    /**
     * Retrieves all selected images from the input list
     *<p>
     * This function wraps the internal downloadImage function to do so for all selected input files, given as a list
     * of ImageDatas. It returns a list of Img objects containing all images on all selected files.
     *</p>

     @param imgs List of ImageDatas containing the selected files given by the user
     @param runStage autoStageRepro instance that will be used to read files
     @return file List of Img objects with all images contained in the downloaded files
     */

    private List<Img> retrieveImagesStage(List<ImageData> imgs, autoStageRepro runStage) throws ExecutionException, DSAccessException, DSOutOfServiceException {
        List<Img> result = new ArrayList<>();
        files = new ArrayList<>();
        for (ImageData img:imgs){
            result.add(downloadImageStage(img.getId(), runStage).get(0));
            files.addAll(tempfiles);
        }

        return result;
    }


    /**
     * Retrieves the ImageData objects for the user-selected files
     *<p>
     * Given the list of indices for which items were picked by the user and the list of all dataset ImageData objects,
     * creates a new list of ImageData objects containing only the ones picked by the user.
     *</p>

     @param selections array of integers with the indices for the user-selected files
     @param images List of ImageData objects will all images in the dataset
     @return result List of ImageData objects with the user-selected images
     */
    
    private List<ImageData> getSelectedImages(int[] selections, List<ImageData> images){

        List<ImageData> result = new ArrayList<>();
        for (int i:selections){
            result.add(images.get(i));
        }
        return result;
    }



    /**
     * main routine function - loads dataset images, creates UI
     * <p>
     *     Fairly straightforward method: setups the OMERO gateway, retrieves all images from the desired dataset
     *     and creates UI elements based on that. Finally, it disconnects the gateway.
     * </p>
     */

    @Override
    public void run() {


//        createUI();

        ij = new net.imagej.ImageJ();
        try {
            setup_gateway();
        } catch (Exception e) {
            e.printStackTrace();
        }
        List<ImageData> images;

        images = connectLoadImages();

        try {
            createUI(images);
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

        autoQC_omero main_class = new autoQC_omero();
        main_class.run();
        }
    }


