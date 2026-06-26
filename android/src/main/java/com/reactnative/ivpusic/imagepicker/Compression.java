package com.reactnative.ivpusic.imagepicker;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.media.ExifInterface;
import android.os.Environment;
import android.text.Layout;
import android.text.StaticLayout;
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
import java.util.ArrayList;
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
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        List<String> lines = buildWatermarkLines(df.format(new Date()), locationInfo, watermarkInfo);
        if (lines.isEmpty()) {
            return bitmap;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Bitmap newBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(newBitmap);

        float scale = Math.max(1f, Math.min(width / 1080f, height / 1440f));
        float padding = 16f * density * scale;
        float margin = 20f * density * scale;
        float radius = 12f * density * scale;
        float titleSize = 20f * density * scale;
        float textSize = 15f * density * scale;
        float lineSpacing = 6f * density * scale;
        float panelWidth = width - margin * 2;

        TextPaint tp = new TextPaint();
        tp.setColor(Color.WHITE);
        tp.setStyle(Paint.Style.FILL);
        tp.setAntiAlias(true);

        String content = joinLines(lines);
        tp.setTextSize(textSize);
        StaticLayout staticLayout = new StaticLayout(content, tp, (int)(panelWidth - padding * 2), Layout.Alignment.ALIGN_NORMAL, 1.0f, lineSpacing, false);
        float titleHeight = titleSize + lineSpacing;
        float panelHeight = padding * 2 + titleHeight + staticLayout.getHeight();
        float left = margin;
        float top = height - panelHeight - margin;

        Paint maskingPaint = new Paint();
        maskingPaint.setColor(Color.argb(150, 0,0,0));
        maskingPaint.setStyle(Paint.Style.FILL);
        maskingPaint.setAntiAlias(true);
        canvas.drawRoundRect(new RectF(left, top, left + panelWidth, top + panelHeight), radius, radius, maskingPaint);

        tp.setTextSize(titleSize);
        tp.setFakeBoldText(true);
        canvas.drawText("现场水印", left + padding, top + padding + titleSize, tp);
        tp.setFakeBoldText(false);
        tp.setTextSize(textSize);

        canvas.save();
        canvas.translate(left + padding, top + padding + titleHeight);
        staticLayout.draw(canvas);
        canvas.restore();
        return newBitmap;
    }

    private List<String> buildWatermarkLines(String timeText, String locationInfo, String watermarkInfo) {
        List<String> lines = new ArrayList<>();
        lines.add("时间：" + timeText);
        if (watermarkInfo != null && watermarkInfo.length() > 0) {
            try {
                JSONObject json = new JSONObject(watermarkInfo);
                addJsonLine(lines, json, "address", "地点");
                addJsonLine(lines, json, "nodeName", "产生单位");
                addJsonLine(lines, json, "vehicleNo", "车牌号");
                addJsonLine(lines, json, "routeName", "路线");
                addJsonLine(lines, json, "operator", "操作人");
                addJsonLine(lines, json, "businessType", "业务");
                addJsonLine(lines, json, "remark", "备注");
            } catch (JSONException ignored) {
                lines.add(watermarkInfo);
            }
        } else if (locationInfo != null && locationInfo.length() > 0) {
            lines.add("地点：" + locationInfo);
        }
        return lines;
    }

    private void addJsonLine(List<String> lines, JSONObject json, String key, String label) {
        String value = json.optString(key, "");
        if (value != null && value.length() > 0 && !"null".equals(value)) {
            lines.add(label + "：" + value);
        }
    }

    private String joinLines(List<String> lines) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                builder.append('\n');
            }
            builder.append(lines.get(i));
        }
        return builder.toString();
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
