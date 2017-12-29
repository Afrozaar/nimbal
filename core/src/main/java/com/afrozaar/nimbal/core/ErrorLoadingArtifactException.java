/*******************************************************************************
 * Nimbal Module Manager 
 * Copyright (c) 2017 Afrozaar.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License 2.0
 * which accompanies this distribution and is available at https://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/
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