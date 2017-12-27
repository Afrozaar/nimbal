package com.afrozaar.nimbal.core;

import com.afrozaar.nimbal.annotations.Module;

import org.springframework.context.annotation.Configuration;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ModuleInfo {
    private Integer order;
    private String name;
    private String parentModule;
    private String parentModuleClassesOnly;
    private List<String> ringFencedFilters;
    private String moduleClass;

    public ModuleInfo() {
        super();
        this.ringFencedFilters = Collections.emptyList();
    }

    public ModuleInfo(Module module, Class<?> moduleClass) {
        this.order = module.order() == Integer.MIN_VALUE ? null : module.order();
        this.name = module.name().equals("") ? moduleClass.getSimpleName() : module.name();
        this.parentModule = StringUtils.stripToNull(module.parentModule());
        if (parentModule == null) {
            this.parentModuleClassesOnly = StringUtils.stripToNull(module.parentModuleClassesOnly());
        }
        this.ringFencedFilters = Arrays.asList(module.ringFenceClassBlackListRegex());
        this.moduleClass = moduleClass.getName();
    }

    public ModuleInfo(Configuration annotation, Class<?> clazz) {
        this.name = StringUtils.stripToNull(annotation.value());
        if (this.name == null) {
            this.name = clazz.getSimpleName();
        }
        this.moduleClass = clazz.getName();
    }

    public Integer order() {
        return order;
    }

    public String name() {
        return name;
    }

    public String parentModule() {
        return parentModule;
    }

    public List<String> ringFenceFilters() {
        return ringFencedFilters;
    }

    public String moduleClass() {
        return moduleClass;
    }

    @Override
    public String toString() {
        return "ModuleInfo [name=" + name + ", parentModule=" + parentModule + ", order=" + order + ", ringFencedFilters=" +
                ringFencedFilters
                + ", moduleClass=" + moduleClass + "]";
    }

    public String parentModuleClassesOnly() {
        return parentModuleClassesOnly;
    }

    public boolean isReloadRequired() {
        return parentModule != null || parentModuleClassesOnly != null || ringFencedFilters != null;
    }

}
