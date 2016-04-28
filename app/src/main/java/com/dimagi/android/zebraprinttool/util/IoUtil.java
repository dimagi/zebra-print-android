package com.dimagi.android.zebraprinttool.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by ctsims on 1/29/2016.
 */
public class IoUtil {

    public static String fileToString(String fileName) throws IOException{
        InputStream is = new FileInputStream(new File(fileName));

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        writeFromInputToOutput(is, baos);
        return baos.toString("UTF-8");
    }

    /**
     * Write is to os and close both
     */
    public static void writeFromInputToOutput(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[8192];

        try {
            int count = is.read(buffer);
            while (count != -1) {
                os.write(buffer, 0, count);
                count = is.read(buffer);
            }
        } finally {
            is.close();
            os.close();
        }
    }

    public static String getExtension(String filePath) {
        if (filePath.contains(".")) {
            return last(filePath.split("\\."));
        }
        return "";
    }
    /**
     * Get the last element of a String array.
     */
    private static String last(String[] strings) {
        return strings[strings.length - 1];
    }


    public static String extractFilename(String filePath) {
        if(filePath.contains(File.separator)) {
            return last(filePath.split(File.separator));
        }
        return filePath;
    }
}
