package org.ssssssss.magicapi.modules.thread;

import org.ssssssss.magicapi.core.annotation.MagicModule;
import org.ssssssss.script.annotation.Comment;

@MagicModule("thread")
public class ThreadModule {

    public ThreadModule() {
    }

    @Comment("让当前线程停顿一段时间")
    public void sleep(
            @Comment(name = "time", value = "interval length for sleeping (in ms)") long time)
            throws InterruptedException {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            throw new InterruptedException("Sleep interrupted");
        }
    }

}
