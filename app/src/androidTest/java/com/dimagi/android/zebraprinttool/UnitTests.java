package com.dimagi.android.zebraprinttool;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static junit.framework.Assert.assertEquals;

/**
 * Created by ctsims on 1/29/2016.
 */
@RunWith(JUnit4.class)
public class UnitTests {
    @Test
    public void testZplFormatNameExtraction() {
        String formatFromFile = "^DFE:tut_tes4.ZPL^FS";
        String format = ZebraPrintTask.attemptToExtractFormatTitle(formatFromFile);
        assertEquals(format, "tut_tes4.ZPL");
    }
}
