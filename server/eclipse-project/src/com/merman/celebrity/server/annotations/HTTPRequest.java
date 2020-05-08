package com.merman.celebrity.server.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@Retention(RetentionPolicy.RUNTIME)
public @interface HTTPRequest {
	String requestName();
	String[] argNames() default {};
}
