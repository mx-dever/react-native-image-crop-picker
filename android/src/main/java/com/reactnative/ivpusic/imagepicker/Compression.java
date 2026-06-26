package com.reactnative.ivpusic.imagepicker;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.media.ExifInterface;
import android.os.Environment;
import android.text.TextPaint;
import android.util.Log;
import android.util.Pair;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableMap;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by ipusic on 12/27/16.
 */

class Compression {

    File resize(
            Context context,
            String originalImagePath,
            int originalWidth,
            int originalHeight,
            int maxWidth,
            int maxHeight,
            int quality,
            boolean enableWaterMarker,
            String locationInfo,
            String watermarkInfo
    ) throws IOException {
        Pair<Integer, Integer> targetDimensions =
                this.calculateTargetDimensions(originalWidth, originalHeight, maxWidth, maxHeight);

        int targetWidth = targetDimensions.first;
        int targetHeight = targetDimensions.second;

        Bitmap bitmap = null;
        if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
            bitmap = BitmapFactory.decodeFile(originalImagePath);
        } else {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = calculateInSampleSize(originalWidth, originalHeight, targetWidth, targetHeight);
            bitmap = BitmapFactory.decodeFile(originalImagePath, options);
        }

        // Use original image exif orientation data to preserve image orientation for the resized bitmap
        ExifInterface originalExif = new ExifInterface(originalImagePath);
        String originalOrientation = originalExif.getAttribute(ExifInterface.TAG_ORIENTATION);

        bitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true);

        File imageDirectory = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);

        if (!imageDirectory.exists()) {
            Log.d("image-crop-picker", "Pictures Directory is not existing. Will create this directory.");
            imageDirectory.mkdirs();
        }

        File resizeImageFile = new File(imageDirectory, UUID.randomUUID() + ".jpg");

        OutputStream os = new BufferedOutputStream(new FileOutputStream(resizeImageFile));
        if (enableWaterMarker) {
            bitmap = handlerWaterRemark(bitmap, context.getResources().getDisplayMetrics().density, locationInfo, watermarkInfo);
        }
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, os);
        // Don't set unnecessary exif attribute
        if (shouldSetOrientation(originalOrientation)) {
            ExifInterface exif = new ExifInterface(resizeImageFile.getAbsolutePath());
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, originalOrientation);
            exif.saveAttributes();
        }

        os.close();
        bitmap.recycle();

        return resizeImageFile;
    }
    private Bitmap handlerWaterRemark(Bitmap bitmap, float density, String locationInfo, String watermarkInfo) {
        Date now = new Date();
        WatermarkData data = buildWatermarkData(now, locationInfo, watermarkInfo);
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(newBitmap);

        float scale = Math.max(1f, Math.min(width / 1080f, height / 1440f));
        float left = 18f * density * scale;
        float bottom = 18f * density * scale;
        float badgeHeight = 48f * density * scale;
        float badgeRadius = 7f * density * scale;
        float badgePadding = 10f * density * scale;
        float tagPaddingHorizontal = 9f * density * scale;
        float yellowWidth = 5f * density * scale;
        float contentLeft = left + yellowWidth + 14f * density * scale;
        float smallTextSize = 15f * density * scale;
        float middleTextSize = 23f * density * scale;
        float dateTextSize = 25f * density * scale;
        float timeTextSize = 28f * density * scale;
        float tagTextSize = 24f * density * scale;
        float lineGap = 12f * density * scale;

        TextPaint tp = new TextPaint();
        tp.setColor(Color.WHITE);
        tp.setStyle(Paint.Style.FILL);
        tp.setAntiAlias(true);

        Paint whiteBg = new Paint();
        whiteBg.setColor(Color.argb(230, 255,255,255));
        whiteBg.setStyle(Paint.Style.FILL);
        whiteBg.setAntiAlias(true);

        Paint yellowBg = new Paint();
        yellowBg.setColor(Color.rgb(255, 198, 39));
        yellowBg.setStyle(Paint.Style.FILL);
        yellowBg.setAntiAlias(true);

        Paint yellowLine = new Paint();
        yellowLine.setColor(Color.rgb(255, 198, 39));
        yellowLine.setStyle(Paint.Style.FILL);
        yellowLine.setAntiAlias(true);

        Paint gradientPaint = new Paint();
        gradientPaint.setShader(new LinearGradient(
                0,
                height * 0.58f,
                0,
                height,
                Color.argb(0, 0, 0, 0),
                Color.argb(115, 0, 0, 0),
                Shader.TileMode.CLAMP
        ));

        tp.setTextSize(tagTextSize);
        tp.setFakeBoldText(true);
        float tagWidth = tp.measureText(data.tagText) + tagPaddingHorizontal * 2;
        tp.setTextSize(timeTextSize);
        float timeWidth = tp.measureText(data.timeText) + badgePadding * 2;
        float badgeWidth = tagWidth + timeWidth;

        float verifyBaseline = height - bottom;
        float dateBaseline = verifyBaseline - smallTextSize - lineGap;
        float addressBaseline = dateBaseline - dateTextSize - lineGap;
        float lineTop = addressBaseline - middleTextSize;
        float lineBottom = verifyBaseline - smallTextSize * 0.15f;
        float badgeBottom = lineTop - 14f * density * scale;
        float badgeTop = badgeBottom - badgeHeight;

        canvas.drawRect(0, height * 0.58f, width, height, gradientPaint);

        canvas.drawRoundRect(new RectF(left, badgeTop, left + badgeWidth, badgeBottom), badgeRadius, badgeRadius, whiteBg);
        canvas.drawRoundRect(new RectF(left + 4f * density * scale, badgeTop + 4f * density * scale, left + tagWidth - 4f * density * scale, badgeBottom - 4f * density * scale), badgeRadius, badgeRadius, yellowBg);

        tp.setFakeBoldText(true);
        tp.setTextSize(tagTextSize);
        tp.setColor(Color.BLACK);
        canvas.drawText(data.tagText, left + tagPaddingHorizontal, badgeTop + badgeHeight * 0.68f, tp);
        tp.setTextSize(timeTextSize);
        tp.setColor(Color.rgb(0, 74, 150));
        canvas.drawText(data.timeText, left + tagWidth + badgePadding, badgeTop + badgeHeight * 0.68f, tp);

        canvas.drawRect(left, lineTop, left + yellowWidth, lineBottom, yellowLine);

        tp.setFakeBoldText(false);
        tp.setShadowLayer(3f * density * scale, 0, 1.5f * density * scale, Color.argb(190, 0,0,0));
        tp.setTextSize(middleTextSize);
        drawOutlinedText(canvas, tp, ellipsize(data.address, tp, width - contentLeft - left), contentLeft, addressBaseline, false);
        tp.setTextSize(dateTextSize);
        drawOutlinedText(canvas, tp, data.dateText, contentLeft, dateBaseline, true);
        tp.setTextSize(smallTextSize);
        drawOutlinedText(canvas, tp, data.verifyText, contentLeft, verifyBaseline, false);
        tp.clearShadowLayer();
        return newBitmap;
    }

    private void drawOutlinedText(Canvas canvas, TextPaint paint, String text, float x, float y, boolean bold) {
        paint.setFakeBoldText(bold);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(2f, paint.getTextSize() * 0.08f));
        paint.setColor(Color.argb(170, 0, 0, 0));
        canvas.drawText(text, x, y, paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawText(text, x, y, paint);
        paint.setFakeBoldText(false);
    }

    private WatermarkData buildWatermarkData(Date date, String locationInfo, String watermarkInfo) {
        DateFormat timeFormat = new SimpleDateFormat("HH:mm");
        DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd EEEE");
        WatermarkData data = new WatermarkData();
        data.tagText = "打卡";
        data.timeText = timeFormat.format(date);
        data.dateText = dateFormat.format(date);
        data.address = locationInfo != null && locationInfo.length() > 0 ? locationInfo : "现场拍照";
        data.verifyText = "SAF APP水印相机已验证 | 时间地点真实";
        if (watermarkInfo != null && watermarkInfo.length() > 0) {
            try {
                JSONObject json = new JSONObject(watermarkInfo);
                data.tagText = json.optString("tagText", data.tagText);
                data.address = json.optString("address", data.address);
                data.verifyText = json.optString("verifyText", data.verifyText);
            } catch (JSONException ignored) {
                data.address = watermarkInfo;
            }
        }
        return data;
    }

    private String ellipsize(String text, TextPaint paint, float maxWidth) {
        if (text == null) {
            return "";
        }
        if (paint.measureText(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        int end = text.length();
        while (end > 0 && paint.measureText(text.substring(0, end) + suffix) > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + suffix;
    }

    private static class WatermarkData {
        String tagText;
        String timeText;
        String address;
        String dateText;
        String verifyText;
    }

    private int calculateInSampleSize(int originalWidth, int originalHeight, int requestedWidth, int requestedHeight) {
        int inSampleSize = 1;

        if (originalWidth > requestedWidth || originalHeight > requestedHeight) {
            final int halfWidth = originalWidth / 2;
            final int halfHeight = originalHeight / 2;

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while ((halfWidth / inSampleSize) >= requestedWidth
                    && (halfHeight / inSampleSize) >= requestedHeight) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }

    private boolean shouldSetOrientation(String orientation) {
        return !orientation.equals(String.valueOf(ExifInterface.ORIENTATION_NORMAL))
                && !orientation.equals(String.valueOf(ExifInterface.ORIENTATION_UNDEFINED));
    }

    File compressImage(final Context context, final ReadableMap options, final String originalImagePath, final BitmapFactory.Options bitmapOptions, final boolean enableWaterMarker, final String locationInfo, final String watermarkInfo) throws IOException {
        Integer maxWidth = options.hasKey("compressImageMaxWidth") ? options.getInt("compressImageMaxWidth") : null;
        Integer maxHeight = options.hasKey("compressImageMaxHeight") ? options.getInt("compressImageMaxHeight") : null;
        Double quality = options.hasKey("compressImageQuality") ? options.getDouble("compressImageQuality") : null;

        boolean isLossLess = (quality == null || quality == 1.0);
        boolean useOriginalWidth = (maxWidth == null || maxWidth >= bitmapOptions.outWidth);
        boolean useOriginalHeight = (maxHeight == null || maxHeight >= bitmapOptions.outHeight);

        List knownMimes = Arrays.asList("image/jpeg", "image/jpg", "image/png", "image/gif", "image/tiff");
        boolean isKnownMimeType = (bitmapOptions.outMimeType != null && knownMimes.contains(bitmapOptions.outMimeType.toLowerCase()));

        if (!enableWaterMarker && isLossLess && useOriginalWidth && useOriginalHeight && isKnownMimeType) {
            Log.d("image-crop-picker", "Skipping image compression");
            return new File(originalImagePath);
        }

        Log.d("image-crop-picker", "Image compression activated");

        // compression quality
        int targetQuality = quality != null ? (int) (quality * 100) : 100;
        Log.d("image-crop-picker", "Compressing image with quality " + targetQuality);

        if (maxWidth == null) maxWidth = bitmapOptions.outWidth;
        if (maxHeight == null) maxHeight = bitmapOptions.outHeight;

        return resize(context, originalImagePath, bitmapOptions.outWidth, bitmapOptions.outHeight, maxWidth, maxHeight, targetQuality, enableWaterMarker, locationInfo, watermarkInfo);
    }

    private Pair<Integer, Integer> calculateTargetDimensions(int currentWidth, int currentHeight, int maxWidth, int maxHeight) {
        int width = currentWidth;
        int height = currentHeight;

        if (width > maxWidth) {
            float ratio = ((float) maxWidth / width);
            height = (int) (height * ratio);
            width = maxWidth;
        }

        if (height > maxHeight) {
            float ratio = ((float) maxHeight / height);
            width = (int) (width * ratio);
            height = maxHeight;
        }

        return Pair.create(width, height);
    }

    synchronized void compressVideo(final Activity activity, final ReadableMap options, final String originalVideo, final String compressedVideo, final Promise promise) {
        // todo: video compression
        // failed attempt 1: ffmpeg => slow and licensing issues
        promise.resolve(originalVideo);
    }
}
