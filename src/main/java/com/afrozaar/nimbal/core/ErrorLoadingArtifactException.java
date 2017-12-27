package com.afrozaar.nimbal.core;

import org.slf4j.helpers.MessageFormatter;

public class ErrorLoadingArtifactException extends Exception {

    public ErrorLoadingArtifactException(String message, Throwable cause) {
        super(message, cause);
    }

    public ErrorLoadingArtifactException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
    }

    public String getError() {
        return "ERROR LOADING ARTIFACT";
    }

}