package com.stdevsec.atlas.identity;

import java.io.Serializable;
import java.util.UUID;

/** The authenticated user as stored in the (JDBC-persisted) session. */
public record AtlasPrincipal(UUID id, String username, boolean admin) implements Serializable {}
