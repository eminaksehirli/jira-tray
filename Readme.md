# JIRA Issues on System Tray

This application shows some information about the JIRA issues on the System
Tray. Information includes keys of JIRA issues that are under development 
and the number of issues that are under review.

You have to login with you JIRA username and password.  It refreshes every 5
minutes.

## How to build

    mvn clean compile assembly:single

## How to run

    java -jar target/jira-tray-1.0-SNAPSHOT-jar-with-dependencies.jar

