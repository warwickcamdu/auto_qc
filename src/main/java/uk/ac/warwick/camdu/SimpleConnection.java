/*
 * To the extent possible under law, the OME developers have waived
 * all copyright and related or neighboring rights to this tutorial code.
 *
 * See the CC0 1.0 Universal license for details:
 *     http://creativecommons.org/publicdomain/zero/1.0/
 */
package uk.ac.warwick.camdu;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import omero.api.ThumbnailStorePrx;
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
    public SecurityContext connect(String hostname, int port, String userName, String password)
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
    public void disconnect()
    {
        gateway.disconnect();
    }

    /** Loads the projects owned by the user currently logged in.*/
    public void loadProjects()
        throws Exception
    {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        Collection<ProjectData> projects = browse.getProjects(ctx);
    }

    @SuppressWarnings("unchecked")
    public List<ImageData> loadImagesInDataset(long datasetId) throws Exception {
        BrowseFacility browse = gateway.getFacility(BrowseFacility.class);
        Collection<ImageData> images = browse.getImagesForDatasets(ctx, Arrays.asList(datasetId));
        Iterator<ImageData> j = images.iterator();
        List<ImageData> imgs = IteratorUtils.toList(j);
        return imgs;
    }

    /** Loads the image with the id 1.*/
    public void loadFirstImage()
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
