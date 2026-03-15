package com.app.billsense.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;

import java.nio.ByteBuffer;

/**
 * Helper class used to convert a camera frame in YUV format to a bitmap format usable by Android.
 *
 * This is a standard utility for working with CameraX's ImageAnalysis use case,
 * which provides images in the YUV_420_888 format.
 */
public class YuvToRgbConverter {

    private final RenderScript rs;
    private final ScriptIntrinsicYuvToRGB scriptYuvToRgb;

    public YuvToRgbConverter(Context context) {
        rs = RenderScript.create(context);
        scriptYuvToRgb = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
    }

    /**
     * Converts an Image in YUV_420_888 format to an RGB Bitmap.
     *
     * @param image The Image from a camera frame, for example from ImageAnalysis.
     * @param output The pre-allocated Bitmap where the converted data will be written.
     */
    public void yuvToRgb(Image image, Bitmap output) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format, expected YUV_420_888");
        }

        // Get the three image planes (Y, U, V) from the Image object.
        Image.Plane[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();

        // Create RenderScript allocations for input and output.
        // The input allocation is created from the YUV data.
        Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs))
                .setX(image.getWidth())
                .setY(image.getHeight())
                .setYuvFormat(ImageFormat.YUV_420_888);

        Allocation yuvAllocation = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

        // The output allocation is created for the final RGB data.
        Type.Builder rgbType = new Type.Builder(rs, Element.RGBA_8888(rs))
                .setX(image.getWidth())
                .setY(image.getHeight());

        Allocation rgbAllocation = Allocation.createTyped(rs, rgbType.create(), Allocation.USAGE_SCRIPT);

        // Copy the YUV data from the byte buffers to the RenderScript allocation.
        yuvAllocation.copyFrom(NV21toJPEG(
                planes[0],
                planes[1],
                planes[2],
                image.getWidth(),
                image.getHeight()
        ));

        // Set the YUV allocation as the input to the conversion script.
        scriptYuvToRgb.setInput(yuvAllocation);

        // Execute the conversion.
        scriptYuvToRgb.forEach(rgbAllocation);

        // Copy the resulting RGB data to the output bitmap.
        rgbAllocation.copyTo(output);
    }

    /**
     * Helper function to convert the YUV_420_888 planes into a single NV21 byte array.
     * RenderScript's `createFromNV21` is more efficient than handling three separate planes.
     */
    private byte[] NV21toJPEG(Image.Plane yPlane, Image.Plane uPlane, Image.Plane vPlane, int width, int height) {
        int ySize = width * height;
        int uvSize = width * height / 4;

        byte[] nv21 = new byte[ySize + uvSize * 2];

        ByteBuffer yBuffer = yPlane.getBuffer();
        ByteBuffer uBuffer = uPlane.getBuffer();
        ByteBuffer vBuffer = vPlane.getBuffer();

        int rowStride = yPlane.getRowStride();
        assert (yPlane.getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // Most efficient case
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        } else { // Slower case: copy row by row
            long yBufferPos = -width;
            for (; pos < ySize; pos += width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = vPlane.getRowStride();
        int pixelStride = vPlane.getPixelStride();

        assert (rowStride == uPlane.getRowStride());
        assert (pixelStride == uPlane.getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // V and U are swapped in NV21 format and are interleaved.
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte) 0);
                if (uBuffer.remaining() > vBuffer.remaining()) {
                    uBuffer.get(nv21, ySize, uBuffer.remaining());
                } else {
                    vBuffer.get(nv21, ySize, vBuffer.remaining());
                }
            } finally {
                vBuffer.put(1, savePixel);
            }
        } else {
            // Slower fallback for less common formats.
            for (int row = 0; row < height / 2; row++) {
                for (int col = 0; col < width / 2; col++) {
                    int vuPos = col * pixelStride + row * rowStride;
                    nv21[pos++] = vBuffer.get(vuPos);
                    nv21[pos++] = uBuffer.get(vuPos);
                }
            }
        }

        return nv21;
    }
}
