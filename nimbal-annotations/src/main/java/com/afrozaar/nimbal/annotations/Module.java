package com.afrozaar.nimbal.annotations;

import org.springframework.context.annotation.Configuration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Configuration
public @interface Module {

    int order() default Integer.MIN_VALUE;

    String name() default "";

    String value() default "";
    /**
     * The module this module depends on (will bcome the parent module, both
     * classloader and bean factory will be included in the chain).
     * <p>
     * If this is specified the parentModuleClassesOnly will be ignored
     * </p>
     */
    String parentModule() default "";

    /**
     * The parent module specified here will only include the class loader as
     * parent (not the bean factory).
     * <p>
     * If the parentModule is specified this value will be ignore
     * </p>
     * 
     */
    String parentModuleClassesOnly() default "";

    String[] ringFenceClassBlackListRegex() default {};
}