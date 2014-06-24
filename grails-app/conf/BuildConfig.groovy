import org.apache.ivy.plugins.resolver.FileSystemResolver

grails.project.dependency.resolver = "maven"

grails.project.dependency.resolution = {
    inherits "global" // inherit Grails' default dependencies
    log "warn"

    repositories {
        grailsHome()
        grailsCentral()
	    mavenCentral()

        mavenRepo "https://repository.intuitive-collaboration.com/nexus/content/repositories/pillarone-public/"
        mavenRepo "https://repository.intuitive-collaboration.com/nexus/content/repositories/pillarone-public-snapshot/"
        mavenRepo "https://ci.canoo.com/nexus/content/repositories/public-releases"
        mavenRepo "http://repo.spring.io/milestone/" //needed for spring-security-core 2.0-rc2 plugin

    }

    String ulcVersion = "7.2.0.6"

    plugins {
        runtime ":background-thread:1.3"
        runtime ":hibernate:3.6.10.3"
        runtime ":quartz:0.4.2"
        runtime ":spring-security-core:2.0-RC2"

        compile "com.canoo:ulc:${ulcVersion}"
        runtime("org.pillarone:pillar-one-ulc-extensions:1.10") { transitive = false }
    }
}

//Change paths to desired risk analytics plugin locations
grails.plugin.location.'risk-analytics-core' = "../risk-analytics-core"
grails.plugin.location.'risk-analytics-application' = "../risk-analytics-application"
//grails.plugin.location.'risk-analytics-life' = "../riskanalytics-life"
grails.plugin.location.'risk-analytics-pc' = "../risk-analytics-property-casualty"
grails.plugin.location.'risk-analytics-pc-cashflow' = "../risk-analytics-pc-cashflow"
grails.plugin.location.'risk-analytics-commons' = "../risk-analytics-commons"
grails.plugin.location.'risk-analytics-reporting' = "../risk-analytics-reporting"
//grails.plugin.location.'art-models' = "../art-models"
//grails.plugin.location.'art-reports' = "../art-reports"
//grails.plugin.location.'risk-analytics-graph-core' = "../risk-analytics-graph-core"
//grails.plugin.location.'risk-analytics-graph-form-editor' = "../risk-analytics-graph-form-editor"
grails.tomcat.jvmArgs= ["-Xms512m",  "-Xmx2G", "-XX:PermSize=512m", "-XX:MaxPermSize=512m"]
