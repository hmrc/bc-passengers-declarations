#!/usr/bin/env bash
sbt clean compile scalafmtAll coverage Test/test it/Test/test dependencyUpdates coverageReport
