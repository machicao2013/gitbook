#!/bin/bash

javac Test.java

java -Xms60m -Xmx60m -Xmn20m -XX:NewRatio=2 -XX:SurvivorRatio=8 -XX:PermSize=30m -XX:MaxPermSize=30m -XX:+PrintGCDetails Test

rm Test.class
