package com.afrozaar.nimbal.core;

import org.slf4j.helpers.MessageFormatter;

public class ModuleLoadException extends Exception {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ModuleLoadException.class);

    public ModuleLoadException(String message, Throwable cause) {
        super(message, cause);
    }

    public ModuleLoadException(String message, Object... args) {
        super(MessageFormatter.arrayFormat(message, args).getMessage());
    }

}
