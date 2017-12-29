## Nimbal

In my work at Afrozaar I developed a mechanism for dynamically loading a spring context. This mechanism can reload the context after changes are made and those changes reflect.

The use case that this applies to is the ability to at runtime add beans to the application, have those beans act on events or other messages the appication sends and have those beans add to the functionality available in the core application. Basically allows you to add behaviour wihtout having to redeploy the whole application and also to deploy certain pieces of code (modules) to only one environment and thus not have that code duplicated accross all environments (which would happen if the code was deployed normally).

This tool has been running in production for about 3 years now and has proved particularly robust and capable.

This project is my efforts to rewrite this facility and open source it. It requires a rewrite to release as in its current state it cannot be used stand alone.

The modules that are loaded must have an @Configuration somewhere or the custom @Module annotation if more sophisticated configuration is required.

So far I have ported/migrated/rewritten

*   Ability to dynamically load a spring context
* Unloading a spring context
* allow one "module" to depend on another modules classes and application context

Still to come features are:

* allow one module to to depend on only another modules classes
* registering the modules so that their classes and application contexts are available to the wider application
* Ability to plugin to a main app (currently only runs as tests)
* Ability to persist the loaded modules state so that on startup they can be reloaded
* Clustering support

The modules must reside in maven and the parameter that is supplied is the maven coordinates (group, artifact and version). Nimbal will scan the jar for any class that has the @Configuration annotation on it and load the application context defined therin. The default application context (the main app's one) will be the parent to this context so any bean in the main app will also be available in these child contexts. If you require more sophisticated config (you want a module to depend on another one) then you use the @Module annotation.

#### How to run the tests:

The only important thing here is that the reactor project has been configured to ensure the test modules (these are the ones that will be loaded) are built before the core module manager is run. There is no hard dependency on or from these test modules - if there was you would not be able to reload modules as they would be loaded from the default class loader.

#### Documentation on Class Reloading 

tl;dr: A class will be unloaded if two conditions are met:

1.  There are no reachable references to objects of that class
2.  It's classloader is not reachable

Thus it is never possible to unload classse that are loaded by the default class loader. 

I'll post some better links when I get a change to find some. Or I'll write my own entry and classloading in java is hard to understand at first but once you get it, it makes a lot of sense.

* <https://stackoverflow.com/questions/45803545/dynamic-loading-of-spring-bean-from-jar-along-with-dependent-beans/45804785#45804785>
* <https://zeroturnaround.com/rebellabs/rebel-labs-tutorial-do-you-really-get-classloaders>







