/**
 * Shared kernel: identifiers, error model, paging and cross-module event types.
 * Every module may depend on it; it depends on nothing in the application.
 */
@org.springframework.modulith.ApplicationModule(type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package com.stdevsec.atlas.shared;
