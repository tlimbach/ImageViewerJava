/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package service;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 *
 * @author thorsten
 */
public class JPGExtractor {

    public List<BufferedImage> createBufferdImageFromMpo(File mpoFile) throws FileNotFoundException, IOException {
        List<Long> mpoOffsets = new ArrayList<>();
        byte[] allMyBytes = Files.readAllBytes(mpoFile.toPath());
        InputStream fs = new BufferedInputStream(new ByteArrayInputStream(allMyBytes));
        final int chunkLength = 16 * 4096;
        final byte[] sig1 = new byte[]{
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe0
        };
        final byte[] sig2 = new byte[]{
            (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xe1
        };

        byte[] tempBytes = new byte[chunkLength];
        long currentOffset = 0;

        while (true) {
            if (fs.read(tempBytes, 0, chunkLength) <= 0) {
                break;
            }
            int sigOffset = searchBytes(tempBytes, sig1, 0, chunkLength);
            if (sigOffset == -1) {
                sigOffset = searchBytes(tempBytes, sig2, 0, chunkLength);
            }
            if (sigOffset >= 0) {
                mpoOffsets.add(currentOffset + sigOffset);
            }
            currentOffset += chunkLength;
        }

        int offsetImg2 = mpoOffsets.get(1).intValue();
        byte[] img1Bytes = new byte[offsetImg2];
        for (int t = 0; t < img1Bytes.length; t++) {
            img1Bytes[t] = allMyBytes[t];
        }

        byte[] img2Bytes = new byte[allMyBytes.length - offsetImg2];
        for (int t = 0; t < img2Bytes.length; t++) {
            img2Bytes[t] = allMyBytes[t + offsetImg2];
        }

        List<BufferedImage> bList = new ArrayList<>();
        bList.add(createBufferedImageFromBytes(img1Bytes));
        bList.add(createBufferedImageFromBytes(img2Bytes));
        return bList;

    }
    
    
     private BufferedImage createBufferedImageFromBytes(byte[] bytes) throws IOException {

        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        return ImageIO.read(bis);
    }

    private int searchBytes(byte[] bytesToSearch, byte[] matchBytes, int startIndex, int count) {
        int ret = -1, max = count - matchBytes.length + 1;
        boolean found;
        for (int i = startIndex; i < max; i++) {
            found = true;
            for (int j = 0; j < matchBytes.length; j++) {
                if (bytesToSearch[i + j] != matchBytes[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                ret = i;
                break;
            }
        }
        return ret;
    }
}