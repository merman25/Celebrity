package com.merman.celebrity.server.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation on a method which also has the {@link HTTPRequest} annotation,
 * to indicate that a new session should be started when the request is
 * received.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface StartNewSession {

}
