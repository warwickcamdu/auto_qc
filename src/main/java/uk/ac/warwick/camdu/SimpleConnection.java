/*
 * To the extent possible under law, the OME developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package uk.ac.warwick.camdu;

import java.util.*;
import java.util.concurrent.ExecutionException;

import omero.api.ThumbnailStorePrx;
import omero.gateway.exception.DSAccessException;
import omero.gateway.exception.DSOutOfServiceException;
import omero.gateway.model.*;
import omero.sys.ParametersI;

import omero.gateway.Gateway;
import omero.gateway.LoginCredentials;
import omero.gateway.SecurityContext;
import omero.gateway.facility.BrowseFacility;
import omero.log.SimpleLogger;
import org.apache.commons.collections.IteratorUtils;


/**
 * A simple connection to an OMERO server using the Java gateway
 *
 * @author The OME Team
 */
public class SimpleConnection {

    /** Reference to the gateway.*/
    private Gateway gateway;

    /** The security context.*/
    private SecurityContext ctx;

    /** 
     * Creates a connection, the gateway will take care of the services
     * life-cycle.
     *
     * @param hostname The name of the server.
     * @param port The port to use.
     * @param userName The name of the user.
     * @param password The user's password.
     */
    SecurityContext connect(String hostname, int port, String userName, String password)
        throws Exception
    {
        LoginCredentials cred = new LoginCredentials();
        cred.getServer().setHostname(hostname);
        if (port > 0) {
            cred.getServer().setPort(port);
        }
        cred.getUser().setUsername(userName);
        cred.getUser().setPassword(password);
        ExperimenterData user = gateway.connect(cred);
        ctx = new SecurityContext(user.getGroupId());
        return ctx;
    }

    /** 
     * Creates a connection, the gateway will take care of the services
     * life-cycle.
     *
     * @param args The arguments used to connect.
     */
    private void connect(String[] args)
        throws Exception
    {
        LoginCredentials cred = new LoginCredentials(args);
        System.out.println(cred.getServer());
        ExperimenterData user = gateway.connect(cred);
        System.out.println("Connected as " + user.getUserName());
        ctx = new SecurityContext(user.getGroupId());
    }
    
    /** Makes sure to disconnect to destroy sessions.*/
    private void disconnect()
    {
        gateway.disconnect();
    }

    /** Loads the projects owned by the user currently logged in.*/
    private void loadProjects()
        throws Exception
    {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        Collection<ProjectData> projects = browse.getProjects(ctx);
    }


    /**
     * Creates a List of ImageData objects with all images in the input dataset.
     *
     * @param datasetId OMERO ID for the dataset.
     * @return imgs List of ImageData objects
     */
    @SuppressWarnings("unchecked")
    List<ImageData> loadImagesInDataset(long datasetId) throws Exception {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        Collection<ImageData> images = browse.getImagesForDatasets(ctx, Arrays.asList(datasetId));
        Iterator<ImageData> j = images.iterator();
        return (List<ImageData>) IteratorUtils.toList(j);
    }



    /**
     * Retrieves the project ID for the project containing the input dataset.
     *
     * @param dsd DatasetData object for the relevant dataset
     * @param ctx SecurityContext for the relevant user
     * @return projId OMERO ID for the containing project
     */
    long get_project(DatasetData dsd, Gateway gateway, SecurityContext ctx) throws DSAccessException, DSOutOfServiceException, ExecutionException {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        Collection<ProjectData> projects = browse.getProjects(ctx);
        Iterator<ProjectData> i = projects.iterator();
        ProjectData project;
        DatasetData dat;
        long projId = -1;
        while (i.hasNext()) {
            project = i.next();
            boolean isThere = false;
            Set<DatasetData> ds = project.getDatasets();
            for (DatasetData d : ds) {
                dat = d;
                if (dat.getId() == dsd.getId()) {
                    isThere = true;
                    projId = project.getId();
                    System.out.println("project ID:");
                    System.out.println(projId);
                    return projId;
                }
            }
        }
    System.out.println("project ID:");
    System.out.println(projId);
    return projId;
    }

    /** Loads the image with the id 1.*/
    private void loadFirstImage()
        throws Exception
    {
        ParametersI params = new ParametersI();
        params.acquisitionData();
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        ImageData image = browse.getImage(ctx, 1L, params);
        PixelsData pixels = image.getDefaultPixels();
        ThumbnailStorePrx store = gateway.getThumbnailService(ctx);
        store.setPixelsId(pixels.getId());
        System.out.println("Ready to get thumbnail");
    }

    /** Creates a new instance.*/
    SimpleConnection()
    {
        gateway = new Gateway(new SimpleLogger());
    }

    /**
     */
    public static void main(String[] args) throws Exception {
        System.out.println(args[1]);
        SimpleConnection client = new SimpleConnection();
        try {
            client.connect(args);
            // Do something e.g. loading user's data.
            // Load the projects/datasets owned by the user currently logged in.
            client.loadProjects();
            client.loadFirstImage();
        } finally {
            client.disconnect();
        }
    }
}
