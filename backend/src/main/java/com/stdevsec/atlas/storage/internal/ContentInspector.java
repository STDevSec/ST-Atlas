package com.stdevsec.atlas.storage.internal;

import com.stdevsec.atlas.shared.UnsupportedMediaException;
import com.stdevsec.atlas.storage.UploadKind;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

/**
 * Decides what a file really is from its bytes (Tika magic detection only, no parsing) and
 * rejects anything outside the allow-list for the upload kind (T-10). Raster images must
 * also have a readable header with sane dimensions (decompression-bomb guard).
 */
@Component
public class ContentInspector {

    static final int MAX_DIMENSION = 12_000;
    static final long MAX_PIXELS = 60_000_000L;

    private final Tika tika = new Tika();

    public String detectAllowed(byte[] bytes, UploadKind kind) {
        String detected = tika.detect(bytes);
        if (!kind.allowedTypes().contains(detected)) {
            throw new UnsupportedMediaException("Unsupported file type");
        }
        if (kind == UploadKind.IMAGE && !"image/webp".equals(detected)) {
            checkImageDimensions(bytes);
        }
        return detected;
    }

    private static void checkImageDimensions(byte[] bytes) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new UnsupportedMediaException("Unreadable image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_DIMENSION || h > MAX_DIMENSION || (long) w * h > MAX_PIXELS) {
                    throw new UnsupportedMediaException("Image dimensions are not allowed");
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            throw new UnsupportedMediaException("Unreadable image");
        }
    }

}
