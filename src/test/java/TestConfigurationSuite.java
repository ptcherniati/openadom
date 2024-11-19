import org.junit.jupiter.api.Tag;
import org.junit.platform.suite.api.*;

@Suite
@SelectPackages("fr.inra.oresing")
@SuiteDisplayName("Tests for BrokenAdom")
@Tag("MODEL_REQUEST_TEST")
@IncludeTags({"UPLOAD_BUNDLE", "SUITE"})
public class TestConfigurationSuite {
}
