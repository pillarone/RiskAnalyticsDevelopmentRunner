import org.apache.ivy.plugins.resolver.URLResolver
import org.apache.ivy.plugins.resolver.FileSystemResolver

grails.project.dependency.resolution = {
    inherits "global" // inherit Grails' default dependencies
    log "warn"

    repositories {
        grailsHome()
        grailsCentral()
    }

    def ulcClientJarResolver = new FileSystemResolver()
    String absolutePluginDir = grailsSettings.projectPluginsDir.absolutePath

    ulcClientJarResolver.addArtifactPattern "${absolutePluginDir}/ulc-[revision]/web-app/lib/[artifact].[ext]"
    ulcClientJarResolver.name = "ulc"

    resolver ulcClientJarResolver


    def myResolver = new URLResolver()
    myResolver.addArtifactPattern "https://svn.intuitive-collaboration.com/GrailsPlugins/grails-[artifact]/tags/LATEST_RELEASE/grails-[artifact]-[revision].[ext]"

    resolver myResolver

    mavenRepo "https://build.intuitive-collaboration.com/maven/plugins/"

    String ulcVersion = "2008-u4-4.1"

    plugins {
        runtime ":background-thread:1.3"
        runtime ":hibernate:1.3.4"
        runtime ":joda-time:0.5"
        runtime ":maven-publisher:0.7.5"
        runtime ":quartz:0.4.1"
        runtime ":spring-security-core:1.0.1"
        runtime ":jetty:1.2-SNAPSHOT"
        runtime ":jdbc-pool:0.3"

        runtime ":tomcat:1.3.4"

        runtime "org.pillarone:jasper:0.9.5-riskanalytics"
        compile "com.canoo:ulc:${ulcVersion}".toString()

        runtime "org.pillarone:risk-analytics-core:1.3-BETA-1.2"
        runtime ("org.pillarone:risk-analytics-application:1.3-BETA-1.2") { transitive = false }
        runtime ("org.pillarone:risk-analytics-property-casualty:1.3-BETA-1") { transitive = false }
    }

    dependencies {
        compile group: 'canoo', name: 'ulc-applet-client', version: ulcVersion
        compile group: 'canoo', name: 'ulc-base-client', version: ulcVersion
        compile group: 'canoo', name: 'ulc-base-trusted', version: ulcVersion
        compile group: 'canoo', name: 'ulc-ejb-client', version: ulcVersion
        compile group: 'canoo', name: 'ulc-jnlp-client', version: ulcVersion
        compile group: 'canoo', name: 'ulc-servlet-client', version: ulcVersion
        compile group: 'canoo', name: 'ulc-standalone-client', version: ulcVersion
    }
}

grails.compiler.dependencies = {
    fileset(dir: "${grailsSettings.projectPluginsDir}", includes: "*/web-app/lib/*.jar")
    fileset(dir: "${basedir}/web-app", includes: "lib/*.jar")
}

//Change paths to desired risk analytics plugin locations
//grails.plugin.location.'risk-analytics-core' = "../risk-analytics-core"
//grails.plugin.location.'risk-analytics-application' = "../risk-analytics-application"
//grails.plugin.location.'risk-analytics-life' = "../risk-analytics-life"
//grails.plugin.location.'risk-analytics-pc' = "../risk-analytics-property-casualty"
