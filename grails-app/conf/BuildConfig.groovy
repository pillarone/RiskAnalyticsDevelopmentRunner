grails.plugin.repos.discovery.pillarone = "https://readplugins:readplugins@svn.intuitive-collaboration.com/GrailsPlugins/"

grails.plugin.repos.resolveOrder = ['pillarone', 'default', 'core']

grails.compiler.dependencies = {
    fileset(dir: "${grailsSettings.projectPluginsDir}", includes: "*/web-app/lib/*.jar")
    fileset(dir: "${basedir}/web-app", includes: "lib/*.jar")
}

//Change paths to desired risk analytics plugin locations

grails.plugin.location.'risk-analytics-core' = "../RiskAnalyticsCore"
grails.plugin.location.'risk-analytics-application' = "../RiskAnalyticsApplication"
grails.plugin.location.'risk-analytics-life' = "../RiskAnalyticsLife"
grails.plugin.location.'risk-analytics-pc' = "../RiskAnalyticsPC"