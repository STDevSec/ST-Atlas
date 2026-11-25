package com.stdevsec.atlas.storage;

import java.io.InputStream;

/** A file opened for reading after authorization. Caller closes the stream. */
public record StoredContent(AttachmentInfo info, InputStream stream) {}
