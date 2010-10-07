<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.0 Transitional//EN">

<HTML>
  <HEAD>
    <TITLE>RiskAnalytics</TITLE>
    <meta name="layout" content="main"/>
  </HEAD>

  <BODY>

    <ulc:applet
            plugin="true"
            userParameter_ViewFactory="org.pillarone.riskanalytics.application.environment.applet.P1RATAppletViewFactory"
            userParameter_java_code="org.pillarone.riskanalytics.application.environment.applet.P1RATAppletLauncher"
            applicationContextPath="plugins/risk-analytics-application-${new RiskAnalyticsApplicationGrailsPlugin().version}"/>
  </BODY>
</HTML>
