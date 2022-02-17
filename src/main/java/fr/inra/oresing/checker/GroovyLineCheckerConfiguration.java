package fr.inra.oresing.checker;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Streams;

import java.util.Set;

/**
 * Configuration pour un checker de type "Expression Groovy"
 */
public interface GroovyLineCheckerConfiguration extends LineCheckerConfiguration {

    GroovyConfiguration getGroovy();
}
