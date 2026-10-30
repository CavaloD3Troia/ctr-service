package org.cavalodetroia.ctr.componenttests;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;

/**
 * Runs every feature under {@code src/test/resources/features} against the whole app, started once
 * with Postgres and Kafka in Testcontainers.
 */
@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "org.cavalodetroia.ctr.componenttests")
class CucumberIT {
}
