import org.apache.ivy.plugins.resolver.FileSystemResolver

grails.project.dependency.resolution = {
    inherits "global" // inherit Grails' default dependencies
    log "warn"

    repositories {
        grailsHome()
        grailsCentral()

        String absolutePluginDir = grailsSettings.projectPluginsDir.absolutePath

        def ulcClientJarResolver = new FileSystemResolver()
        ulcClientJarResolver.addArtifactPattern "${absolutePluginDir}/ulc-[revision]/web-app/lib/[artifact].[ext]"
        ulcClientJarResolver.addArtifactPattern "${basedir}/web-app/lib/[artifact]-[revision].[ext]"
        ulcClientJarResolver.name = "ulc"
        resolver ulcClientJarResolver

        mavenRepo "https://repository.intuitive-collaboration.com/nexus/content/repositories/pillarone-public/"
        mavenRepo "https://ci.canoo.com/nexus/content/repositories/public-releases"

    }

    String ulcVersion = "ria-suite-u5"

    plugins {
        runtime ":background-thread:1.3"
        runtime ":hibernate:2.2.1"
        runtime ":joda-time:0.5"
        runtime ":maven-publisher:0.7.5", {
            excludes "groovy"
        }
        runtime ":quartz:0.4.2"
        runtime ":spring-security-core:1.2.7.3"

        compile "com.canoo:ulc:${ulcVersion}"
        runtime("org.pillarone:pillar-one-ulc-extensions:0.3") { transitive = false }
    }

    dependencies {
        compile group: 'canoo', name: 'ulc-applet-client', version: ulcVersion
        compile group: 'canoo', name: 'ulc-base-client', version: ulcVersion
        compile group: 'canoo', name: 'ulc-base-trusted', version: ulcVersion
        compile group: 'canoo', name: 'ulc-jnlp-client', version: ulcVersion
        compile group: 'canoo', name: 'ulc-servlet-client', version: ulcVersion
        compile group: 'canoo', name: 'ulc-standalone-client', version: ulcVersion
    }
}

//Change paths to desired risk analytics plugin locations
grails.plugin.location.'risk-analytics-core' = "../risk-analytics-core"
grails.plugin.location.'risk-analytics-application' = "../risk-analytics-application"
grails.plugin.location.'risk-analytics-life' = "../riskanalytics-life"
grails.plugin.location.'risk-analytics-pc' = "../risk-analytics-property-casualty"
grails.plugin.location.'risk-analytics-pc-cashflow' = "../risk-analytics-pc-cashflow"
grails.plugin.location.'risk-analytics-commons' = "../risk-analytics-commons"
grails.plugin.location.'art-models' = "../art-models"
//grails.plugin.location.'risk-analytics-graph-core' = "../RiskAnalyticsGraphCore"
//grails.plugin.location.'risk-analytics-graph-form-editor' = "../RiskAnalyticsGraphFormEditor"
