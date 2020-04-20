package com.merman.celebrity.server.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import com.merman.celebrity.server.RequestType;

@Retention(RetentionPolicy.RUNTIME)
public @interface HTTPRequest {
	String requestName();
	RequestType requestType() default RequestType.GET_OR_POST;
	String[] argNames() default {};
}
