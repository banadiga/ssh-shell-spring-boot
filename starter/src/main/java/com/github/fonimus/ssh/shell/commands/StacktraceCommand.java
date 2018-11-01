package com.github.fonimus.ssh.shell.commands;

import com.github.fonimus.ssh.shell.postprocess.TypePostProcessorResultHandler;
import org.jline.terminal.Terminal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.commands.Stacktrace;

/**
 * Override stacktrace command to get error per thread
 */
@SshShellComponent
public class StacktraceCommand implements Stacktrace.Command {

    @Autowired
    @Lazy
    private Terminal terminal;

    @ShellMethod(key = {"stacktrace"}, value = "Display the full stacktrace of the last error.")
    public void stacktrace() {
        Throwable lastError = TypePostProcessorResultHandler.THREAD_CONTEXT.get();
        if (lastError != null) {
            lastError.printStackTrace(this.terminal.writer());
        }

    }
}
