package com.mylearning.myspringmongodb.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT)
public class PersonAlreadyExistException extends RuntimeException{
    public PersonAlreadyExistException(String message) {
        super(message);
    }
}
