import org.apache.ivy.plugins.resolver.URLResolver

grails.project.dependency.resolution = {
    inherits "global" // inherit Grails' default dependencies
    log "warn"

    repositories {
        grailsHome()
        grailsCentral()
    }

    def myResolver = new URLResolver()
    myResolver.addArtifactPattern "https://svn.intuitive-collaboration.com/GrailsPlugins/grails-[artifact]/tags/LATEST_RELEASE/grails-[artifact]-[revision].[ext]"

    resolver myResolver
}

grails.compiler.dependencies = {
    fileset(dir: "${grailsSettings.projectPluginsDir}", includes: "*/web-app/lib/*.jar")
    fileset(dir: "${basedir}/web-app", includes: "lib/*.jar")
}

//Change paths to desired risk analytics plugin locations
grails.plugin.location.'risk-analytics-core' = "../RiskAnalyticsCore"
grails.plugin.location.'risk-analytics-application' = "../RiskAnalyticsApplication"
grails.plugin.location.'risk-analytics-life' = "../RiskAnalyticsLife"
grails.plugin.location.'risk-analytics-pc' = "../RiskAnalyticsPC"
