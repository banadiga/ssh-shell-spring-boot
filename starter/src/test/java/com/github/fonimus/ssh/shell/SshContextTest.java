package com.github.fonimus.ssh.shell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

public class SshContextTest {

    @Test
    void test() {
        SshContext ctx = new SshContext(null, null);
        assertNull(ctx.getExitCallback());
        assertNull(ctx.getThread());
    }
}