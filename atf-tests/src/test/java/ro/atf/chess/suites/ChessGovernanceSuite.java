package ro.atf.chess.suites;

import atf.testkit.scope.TestRunScope;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/** Suite inventory, audit, documentation, release and AI policy contracts. */
@Suite
@SelectPackages("ro.atf.chess")
@IncludeClassNamePatterns("^[^$]+Test$")
@IncludeEngines("junit-jupiter")
@IncludeTags("component & governance")
@ConfigurationParameter(key = TestRunScope.CONFIGURATION_PARAMETER, value = "suite")
@ConfigurationParameter(key = "chess.suite", value = "ChessGovernanceSuite")
public class ChessGovernanceSuite { }
