package com.stdevsec.atlas.storage.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.stdevsec.atlas.shared.UnsupportedMediaException;
import com.stdevsec.atlas.storage.UploadKind;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** T-10: the type comes from the bytes; dangerous or disguised content is rejected. */
class ContentInspectorTest {

    private final ContentInspector inspector = new ContentInspector();

    static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    void acceptsRealPng() throws IOException {
        assertThat(inspector.detectAllowed(png(10, 10), UploadKind.IMAGE)).isEqualTo("image/png");
    }

    @Test
    void rejectsSvgWithScript() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> inspector.detectAllowed(svg, UploadKind.IMAGE))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void rejectsHtmlPretendingToBeAnImage() {
        byte[] html = "<html><body onload=alert(1)>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> inspector.detectAllowed(html, UploadKind.IMAGE))
                .isInstanceOf(UnsupportedMediaException.class);
    }

    @Test
    void rejectsPdfWhereAnImageIsExpected() {
        byte[] pdf = "%PDF-1.7\n1 0 obj<<>>endobj\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> inspector.detectAllowed(pdf, UploadKind.IMAGE))
                .isInstanceOf(UnsupportedMediaException.class);
        assertThat(inspector.detectAllowed(pdf, UploadKind.PDF)).isEqualTo("application/pdf");
    }

    @Test
    void rejectsOversizedDimensions() throws IOException {
        byte[] huge = png(12_001, 1);
        assertThatThrownBy(() -> inspector.detectAllowed(huge, UploadKind.IMAGE))
                .isInstanceOf(UnsupportedMediaException.class);
    }
}
