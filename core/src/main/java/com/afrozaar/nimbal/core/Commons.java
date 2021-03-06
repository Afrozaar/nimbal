/*******************************************************************************
 * Nimbal Module Manager 
 * Copyright (c) 2017 Afrozaar.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Apache License 2.0
 * which accompanies this distribution and is available at https://www.apache.org/licenses/LICENSE-2.0
 *******************************************************************************/
package com.afrozaar.nimbal.core;

import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

public final class Commons {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Commons.class);

    private Commons() {

    }

    public static URL[] getJars(DependencyNode node) {
        PreorderNodeListGenerator nlg = new PreorderNodeListGenerator();
        node.accept(nlg);
        URL[] urls = nlg.getFiles().stream().map(file -> {
            try {
                return new URL("file", null, file.getAbsolutePath());
            } catch (MalformedURLException e) {
                LOG.info("error creating url file {}", file, e);
                return null;
            }
        }).filter(Objects::nonNull).toArray(URL[]::new);
        return urls;
    }

}
