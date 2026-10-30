package com.devtraining.tickets.common.exception;

import org.springframework.http.HttpStatus;

public class NotFoundException extends ApiException {

	public NotFoundException(String resource, Object id) {
		super(HttpStatus.NOT_FOUND, "%s %s not found".formatted(resource, id));
	}

}
