package com.afrozaar.nimbal.core.classloader;

import com.google.common.collect.Lists;

import org.springframework.core.SmartClassLoader;
import org.springframework.util.AntPathMatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Predicate;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class URLClassLoaderExtension extends URLClassLoader implements SmartClassLoader {

    private final String name;

    Logger LOG = LoggerFactory.getLogger(this.getClass());

    private List<String> ringFencedFilters = Lists.newLinkedList();

    private static int count;
    int id = count++;

    public URLClassLoaderExtension(URL[] urls, ClassLoader parent, String name) {
        super(urls, parent);
        this.name = name;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        LOG.trace("in find class {} class {}", this, name);
        return super.findClass(name);
    }

    @Override
    public URL findResource(String name) {
        LOG.trace("in find resource {}  {} ", this, name);
        return super.findResource(name);
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        LOG.trace("in load class {} {}", this, name);
        return super.loadClass(name);
    }

    private static final AntPathMatcher antPathMatcher = new AntPathMatcher(".");

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        LOG.trace("in load class {} {}, {}", name, this, name, resolve);

        boolean loadClassLocally = isLoadClassLocally(name, ringFencedFilters);

        if (loadClassLocally) {
            LOG.debug("loading class {} from module and not asking parent", name);
            Class<?> findClass = findLoadedClass(name);
            if (findClass == null) {
                findClass = findClass(name);
            }
            if (resolve) {
                resolveClass(findClass);
            }
            LOG.debug("class {} successfully loaded from module", name);
            return findClass;
        } else {
            return super.loadClass(name, resolve);
        }
    }

    static boolean isLoadClassLocally(String name, List<String> ringFencedFilters) {
        Predicate<String> regexFilter = filter -> {
            try {
                return Pattern.matches(filter, name);
            } catch (PatternSyntaxException pse) {
                /* we auto select between regex compliant filters and "ant path matchers" so we need to silently swallow invalid regular expressions */
                return false;
            }
        };
        Predicate<String> antPathFilter = filter -> antPathMatcher.match(filter, name);

        return ringFencedFilters.stream().anyMatch(antPathFilter.or(regexFilter));
    }

    @Override
    public String toString() {
        return "[ URL Class Loader - " + name + " " + id + " ]";
    }

    @Override
    public boolean isClassReloadable(Class<?> clazz) {
        LOG.debug("is class reloadable called for {}, returning true", clazz);
        return true;
    }

    public List<String> getRingFencedFilters() {
        return ringFencedFilters;
    }

    public void setRingFencedFilters(List<String> ringFencedFilters) {
        this.ringFencedFilters = ringFencedFilters;
    }

    public String getName() {
        return name;
    }
}
