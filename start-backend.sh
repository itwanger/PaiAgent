#!/bin/bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@21/21.0.9/libexec/openjdk.jdk/Contents/Home
cd console/backend/hub
mvn spring-boot:run -Dspring-boot.run.profiles=minimal
