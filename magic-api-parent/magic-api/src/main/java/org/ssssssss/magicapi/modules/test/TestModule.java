package org.ssssssss.magicapi.modules.test;

import org.springframework.stereotype.Component;
import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.magicapi.core.logging.Log4j2LoggerContext;
import org.ssssssss.magicapi.core.logging.Log4jLoggerContext;
import org.ssssssss.script.annotation.Comment;

/**
 * Test module for functional testing
 */
@Component
@MagicModule("test")
public class TestModule {

    public TestModule() {
    }

    @Comment("Printing something.")
    public void println(@Comment("Printing the input value.") String value) {
        System.out.println("you're using println method from test module: " + value);
    }

}
