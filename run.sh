#!/bin/bash
./gradlew build
java -jar build/libs/graph-1.0-SNAPSHOT.jar --config src/config/config.xml