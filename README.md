## Nimbal

In my work at Afrozaar I developed a mechanism for dynamically loading a spring context. This mechanism can reload the context after changes are made and those changes reflect.

The use case that this applies to is the ability to at runtime add beans to teh application, have those beans act on events or other messages the appication sends and have those beans add to the functionality available in the core application. Basically allows you to add behaviour wihtout having to redeploy the whole application and also to deploy certain pieces of code (modules) to only one environment and thus not have that code duplicated accross all environments (which would happen if the code was deployed normally).

This tool has been running in production for about 3 years now and has proved particularly robust and capable.

This project is my efforts to rewrite this facility and open source it. It requires a rewrite to release as in its current state it cannot be used stand alone.

The modules that are loaded must have an @Configuration somewhere or the custom @Module annotation if more sophisticated configuration is required.

So far I have ported/migrated/rewritten

*   Ability to dynamically load a spring context

Still to come features are:

* Unloading a spring context
* allow one "module" to depend on another modules classes and application context
* allow one module to to depend on only another modules classes
* registering the modules so that their classes and application contexts are available to the wider application
* allow the customising of the contexts before they are refreshed - this means you can for example add configuration properties or add a bean post processor to act on relevant beans.
* Ability to plugin to a main app (currently only runs as tests)
* Ability to persist the loaded modules state so that on startup they can be reloaded
* Clustering support

The modules must reside in maven and the parameter that is supplied is the maven coordinates (group, artifact and version). Nimbal will scan the jar for any class that has the @Configuration annotation on it and load the application context defined therin. The default application context (the main app's one) will be the parent to this context so any bean in the main app will also be available in these child contexts. If you require more sophisticated config (you want a module to depend on another one) then you use the @Module annotation.

#### How to run the tests:

Since this solution relies heavily on maven we need to first create the test modules. run mvn intall in the nimbal-test pom to install these locally. Once this is done the tests in nimbal-core can be run that will demonstrate the module manager in action.

I'm not sure whether I'll add these test modules to maven central so that this step is not required. This is probably not required because 


