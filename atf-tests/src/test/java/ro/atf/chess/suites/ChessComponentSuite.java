package ro.atf.chess.suites;

import atf.testkit.scope.TestRunScope;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/** Offline product contracts. */
@Suite
@SelectPackages("ro.atf.chess")
@IncludeClassNamePatterns("^[^$]+Test$")
@IncludeEngines("junit-jupiter")
@IncludeTags("component & !governance & !benchmark")
@ConfigurationParameter(key = TestRunScope.CONFIGURATION_PARAMETER, value = "suite")
@ConfigurationParameter(key = "chess.suite", value = "ChessComponentSuite")
public class ChessComponentSuite { }
