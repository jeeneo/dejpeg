package com.je.dejpeg.utils;

import android.graphics.Bitmap;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class bmpEncoder {
    private static final int BMP_HEADER_SIZE = 14;
    private static final int DIB_HEADER_SIZE = 40;
    
    public static void saveBitmap(Bitmap bitmap, OutputStream outputStream) throws IOException {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        int rowPadding = (4 - ((width * 3) % 4)) % 4;
        int rowSize = width * 3 + rowPadding;
        int fileSize = BMP_HEADER_SIZE + DIB_HEADER_SIZE + (rowSize * height);
        
        ByteBuffer bmpBuffer = ByteBuffer.allocate(BMP_HEADER_SIZE);
        bmpBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        bmpBuffer.put((byte) 'B');
        bmpBuffer.put((byte) 'M');
        bmpBuffer.putInt(fileSize);
        bmpBuffer.putShort((short) 0);
        bmpBuffer.putShort((short) 0);
        bmpBuffer.putInt(BMP_HEADER_SIZE + DIB_HEADER_SIZE);
        
        outputStream.write(bmpBuffer.array());
        
        ByteBuffer dibBuffer = ByteBuffer.allocate(DIB_HEADER_SIZE);
        dibBuffer.order(ByteOrder.LITTLE_ENDIAN);
        
        dibBuffer.putInt(DIB_HEADER_SIZE);
        dibBuffer.putInt(width);
        dibBuffer.putInt(height);
        dibBuffer.putShort((short) 1);
        dibBuffer.putShort((short) 24);
        dibBuffer.putInt(0);
        dibBuffer.putInt(rowSize * height);
        dibBuffer.putInt(0);
        dibBuffer.putInt(0);
        dibBuffer.putInt(0);
        dibBuffer.putInt(0);
        
        outputStream.write(dibBuffer.array());
        
        byte[] rowBuffer = new byte[rowSize];
        int[] pixels = new int[width];
        
        for (int y = height - 1; y >= 0; y--) {
            bitmap.getPixels(pixels, 0, width, 0, y, width, 1);
            
            int rowBufferPos = 0;
            for (int x = 0; x < width; x++) {
                int pixel = pixels[x];
                rowBuffer[rowBufferPos++] = (byte) (pixel & 0xFF);
                rowBuffer[rowBufferPos++] = (byte) ((pixel >> 8) & 0xFF);
                rowBuffer[rowBufferPos++] = (byte) ((pixel >> 16) & 0xFF);
            }
            
            for (int i = 0; i < rowPadding; i++) {
                rowBuffer[rowBufferPos++] = 0;
            }
            
            outputStream.write(rowBuffer);
        }
        
        outputStream.flush();
    }
}
