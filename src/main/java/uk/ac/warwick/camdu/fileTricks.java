package uk.ac.warwick.camdu;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//


import ij.IJ;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

class fileTricks {
    private fileTricks() {
    }

    public static void save(String content, String path) {
        try {
            BufferedWriter file = new BufferedWriter(new FileWriter(path));
            file.write(content, 0, content.length());
            file.close();
        } catch (IOException var3) {
            Logger.getLogger(fileTricks.class.getName()).log(Level.SEVERE, (String)null, var3);
        }

    }

    public static void saveRoi(Roi roi, String path) {
        try {
            RoiEncoder re = new RoiEncoder(path);
            re.write(roi);
        } catch (IOException var3) {
            System.out.println("Can't save roi");
        }

    }

    public static void saveRois(Roi[] rois, String path) {
        try {
            ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos));
            RoiEncoder re = new RoiEncoder(out);

            for (Roi roi : rois) {
                if (roi != null) {
                    String label = roi.getName();
                    if (!label.endsWith(".roi")) {
                        label = label + ".roi";
                    }

                    zos.putNextEntry(new ZipEntry(label));
                    re.write(roi);
                    out.flush();
                }
            }

            out.close();
        } catch (IOException var8) {
            System.out.println("Can't save rois");
        }

    }

    public static Vector<String[]> load(String path) {
        Vector out = new Vector();

        try {
            BufferedReader file = new BufferedReader(new FileReader(path));

            for(String line = file.readLine(); line != null; line = file.readLine()) {
                //noinspection unchecked
                out.add(line.split("\t"));
            }

            file.close();
        } catch (IOException var4) {
            Logger.getLogger(fileTricks.class.getName()).log(Level.SEVERE, (String)null, var4);
        }

        //noinspection unchecked
        return out;
    }

    public static void showPdf(String path) {
        if (IJ.isWindows()) {
            try {
                String cmd = "rundll32 url.dll,FileProtocolHandler \"" + path + "\"";
                Runtime.getRuntime().exec(cmd);
            } catch (IOException var3) {
                Logger.getLogger(fileTricks.class.getName()).log(Level.SEVERE, (String)null, var3);
            }
        }

        if (IJ.isMacintosh() || IJ.isLinux()) {
            try {
                String[] cmd = new String[]{"open", path};
                Runtime.getRuntime().exec(cmd);
            } catch (IOException var2) {
                Logger.getLogger(fileTricks.class.getName()).log(Level.SEVERE, (String)null, var2);
            }
        }

    }
}
