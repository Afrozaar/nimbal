package com.afrozaar.nimbal.core;

import com.afrozaar.nimbal.annotations.Module;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;

class ModuleInfo {
    private Integer order;
    private String name;
    private String parentModule;
    private String parentModuleClassesOnly;
    private String[] ringFencedFilters;
    private String moduleClass;

    public ModuleInfo() {
        super();
    }

    public ModuleInfo(Module module, Class<?> moduleClass) {
        this.order = module.order() == Integer.MIN_VALUE ? null : module.order();
        this.name = module.name().equals("") ? moduleClass.getSimpleName() : module.name();
        this.parentModule = StringUtils.stripToNull(module.parentModule());
        if (parentModule == null) {
            this.parentModuleClassesOnly = StringUtils.stripToNull(module.parentModuleClassesOnly());
        }
        this.ringFencedFilters = module.ringFenceClassBlackListRegex();
        this.moduleClass = moduleClass.getName();
    }

    Integer order() {
        return order;
    }

    String name() {
        return name;
    }

    String parentModule() {
        return parentModule;
    }

    String[] ringFenceFilters() {
        return ringFencedFilters;
    }

    public String moduleClass() {
        return moduleClass;
    }

    @Override
    public String toString() {
        return "ModuleInfo [name=" + name + ", parentModule=" + parentModule + ", order=" + order + ", ringFencedFilters=" + Arrays.toString(
                ringFencedFilters)
                + ", moduleClass=" + moduleClass + "]";
    }

    public String parentModuleClassesOnly() {
        return parentModuleClassesOnly;
    }

}
