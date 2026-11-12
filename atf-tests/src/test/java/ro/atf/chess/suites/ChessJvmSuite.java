package ro.atf.chess.suites;

import atf.testkit.scope.TestRunScope;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

/** Maven entry point. Benchmarks activate only with their documented inputs. */
@Suite
@SelectPackages("ro.atf.chess")
@IncludeClassNamePatterns("^[^$]+Test$")
@IncludeEngines("junit-jupiter")
@IncludeTags("component")
@ConfigurationParameter(key = TestRunScope.CONFIGURATION_PARAMETER, value = "suite")
@ConfigurationParameter(key = "chess.suite", value = "ChessJvmSuite")
public class ChessJvmSuite { }
