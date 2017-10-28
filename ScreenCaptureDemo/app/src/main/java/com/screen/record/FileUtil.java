package com.screen.record;

import android.os.Environment;

import java.io.File;

/**
 * Created by panwenjuan on 17-7-28.
 */
public class FileUtil {

    public static final String FILE_NAME = "test.mp4";

    public static String getSaveDirectory() {
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            String rootDir = Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + "ScreenRecord" + "/";

            File file = new File(rootDir);
            if (!file.exists()) {
                if (!file.mkdirs()) {
                    return null;
                }
            }

            return rootDir;
        } else {
            return null;
        }
    }
}
