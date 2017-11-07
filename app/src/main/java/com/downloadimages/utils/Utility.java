package com.downloadimages.utils;

import android.graphics.Bitmap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Created by ankushbadlas on 07/11/17.
 */

public class Utility {
    public static boolean isDebug = true;

    public static String saveImageToStorage(Bitmap mSelectedImage, String dirPath, String fileName) {
        OutputStream fout = null;

        File imageFile = new File(dirPath + fileName);
//        File imageFile = Utility.WorkoutImagesPath();

        if (!imageFile.exists()) {
            imageFile.mkdirs();
        }
        imageFile = new File(imageFile, fileName);
        try {
            fout = new FileOutputStream(imageFile);
            if (fileName.contains("png") || fileName.contains("PNG"))
                mSelectedImage.compress(Bitmap.CompressFormat.PNG, 100, fout);
            else
                mSelectedImage.compress(Bitmap.CompressFormat.JPEG, 100, fout);
            fout.flush();
            fout.close();

        } catch (IOException e) {

        }
        return imageFile.getAbsolutePath();
    }

    //1 minute = 60 seconds
    //1 hour = 60 x 60 = 3600
    //1 day = 3600 x 24 = 86400
    public static String printDifference(Date startDate) {

        Date endDate = Calendar.getInstance(Locale.getDefault()).getTime();

        //milliseconds
        long different = endDate.getTime() - startDate.getTime();
        if (isDebug) {
            System.out.println("startDate : " + startDate);
            System.out.println("endDate : " + endDate);
            System.out.println("different : " + different);
        }
        long secondsInMillis = 10000;
        long secondsInMilli = 1000;
        long minutesInMilli = secondsInMilli * 60;
        long hoursInMilli = minutesInMilli * 60;
        long daysInMilli = hoursInMilli * 24;

        long elapsedDays = different / daysInMilli;
        different = different % daysInMilli;

        long elapsedHours = different / hoursInMilli;
        different = different % hoursInMilli;

        long elapsedMinutes = different / minutesInMilli;
        different = different % minutesInMilli;

        long elapsedSeconds = different / secondsInMilli;

        long elapsedMilliSeconds = different / secondsInMillis;

        System.out.printf("%d days, %d hours, %d minutes, %d seconds%n",
                elapsedDays, elapsedHours, elapsedMinutes, elapsedSeconds);

        String timeTaken;
        if (elapsedDays > 0)
            timeTaken = elapsedDays + " days " + elapsedHours + " hours " + elapsedMinutes + " minutes " + elapsedSeconds + " seconds";
        else if (elapsedHours > 0)
            timeTaken = elapsedHours + " hours" + elapsedMinutes + " minutes " + elapsedSeconds + " seconds";
        else if (elapsedMinutes > 0)
            timeTaken = elapsedMinutes + " minutes " + elapsedSeconds + " seconds";
        else if (elapsedMinutes > 0)
            timeTaken = elapsedSeconds + " seconds " + elapsedMilliSeconds + " milliseconds";
        else
            timeTaken = elapsedMilliSeconds + " milliseconds";
        return timeTaken;
    }
}
