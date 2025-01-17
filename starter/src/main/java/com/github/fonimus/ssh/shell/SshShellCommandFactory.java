package com.github.fonimus.ssh.shell;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.sshd.common.Factory;
import org.apache.sshd.server.ChannelSessionAware;
import org.apache.sshd.server.ExitCallback;
import org.apache.sshd.server.Signal;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.command.Command;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.Parser;
import org.jline.terminal.Attributes;
import org.jline.terminal.Size;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.Banner;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.shell.ExitRequest;
import org.springframework.shell.Input;
import org.springframework.shell.Shell;
import org.springframework.shell.jline.InteractiveShellApplicationRunner;
import org.springframework.shell.jline.JLineShellAutoConfiguration;
import org.springframework.shell.jline.PromptProvider;
import org.springframework.shell.result.DefaultResultHandler;
import org.springframework.stereotype.Component;

import com.github.fonimus.ssh.shell.auth.SshAuthentication;
import com.github.fonimus.ssh.shell.auth.SshShellSecurityAuthenticationProvider;

import static com.github.fonimus.ssh.shell.SshShellHistoryAutoConfiguration.HISTORY_FILE;

/**
 * Ssh shell command factory implementation
 */
@Slf4j
@Component
public class SshShellCommandFactory
		implements Command, Factory<Command>, ChannelSessionAware, Runnable {

	public static final ThreadLocal<SshContext> SSH_THREAD_CONTEXT = ThreadLocal.withInitial(() -> null);

	private InputStream is;

	private OutputStream os;

	private ExitCallback ec;

	private Thread sshThread;

	private ChannelSession session;

	private Banner shellBanner;

	private PromptProvider promptProvider;

	private Shell shell;

	private JLineShellAutoConfiguration.CompleterAdapter completerAdapter;

	private final Parser parser;

	private Environment environment;

	private File historyFile;

	private org.apache.sshd.server.Environment sshEnv;

	private boolean displayBanner;

	/**
	 * Constructor
	 *
	 * @param banner           shell banner
	 * @param promptProvider   prompt provider
	 * @param shell            spring shell
	 * @param completerAdapter completer adapter
	 * @param parser           jline parser
	 * @param environment      spring environment
	 * @param historyFile      history file location
	 * @param properties       ssh shell properties
	 */
	public SshShellCommandFactory(@Autowired(required = false) Banner banner, @Lazy PromptProvider promptProvider, Shell shell,
			JLineShellAutoConfiguration.CompleterAdapter completerAdapter, Parser parser, Environment environment,
			@Qualifier(HISTORY_FILE) File historyFile, SshShellProperties properties) {
		this.shellBanner = banner;
		this.promptProvider = promptProvider;
		this.shell = shell;
		this.completerAdapter = completerAdapter;
		this.parser = parser;
		this.environment = environment;
		this.historyFile = historyFile;
		this.displayBanner = properties.isDisplayBanner();
	}

	/**
	 * Start ssh session
	 *
	 * @param channelSession ssh channel session
	 * @param env            ssh environment
	 */
	@Override
	public void start(ChannelSession channelSession, org.apache.sshd.server.Environment env) throws IOException {
		LOGGER.debug("{}: start", session.toString());
		sshEnv = env;
		sshThread = new Thread(this, "ssh-session-" + System.nanoTime());
		sshThread.start();
	}

	/**
	 * Run ssh session
	 */
	@Override
	public void run() {
		LOGGER.debug("{}: run", session.toString());
		Size size = new Size(Integer.parseInt(sshEnv.getEnv().get("COLUMNS")), Integer.parseInt(sshEnv.getEnv().get("LINES")));
		try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
				PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8.name());
				Terminal terminal = TerminalBuilder.builder().system(false).size(size).type(sshEnv.getEnv().get("TERM")).streams(is, os).build()) {

			DefaultResultHandler resultHandler = new DefaultResultHandler();
			resultHandler.setTerminal(terminal);

			Attributes attr = terminal.getAttributes();
			SshShellUtils.fill(attr, sshEnv.getPtyModes());
			terminal.setAttributes(attr);

			sshEnv.addSignalListener((channel, signal) -> {
				terminal.setSize(new Size(
						Integer.parseInt(sshEnv.getEnv().get("COLUMNS")),
						Integer.parseInt(sshEnv.getEnv().get("LINES"))));
				terminal.raise(Terminal.Signal.WINCH);
			}, Signal.WINCH);

			if (displayBanner && shellBanner != null) {
				shellBanner.printBanner(environment, this.getClass(), ps);
			}
			resultHandler.handleResult(new String(baos.toByteArray(), StandardCharsets.UTF_8));
			resultHandler.handleResult("Please type `help` to see available commands");

			LineReader reader = LineReaderBuilder.builder()
					.terminal(terminal)
					.appName("Spring Ssh Shell")
					.completer(completerAdapter)
					.highlighter((reader1, buffer) -> {
						int l = 0;
						String best = null;
						for (String command : shell.listCommands().keySet()) {
							if (buffer.startsWith(command) && command.length() > l) {
								l = command.length();
								best = command;
							}
						}
						if (best != null) {
							return new AttributedStringBuilder(buffer.length()).append(best, AttributedStyle.BOLD).append(buffer.substring(l)).toAttributedString();
						} else {
							return new AttributedString(buffer, AttributedStyle.DEFAULT.foreground(AttributedStyle.RED));
						}
					})
					.parser(parser)
					.build();
			reader.setVariable(LineReader.HISTORY_FILE, historyFile.toPath());

			Object authenticationObject = session.getSession().getIoSession().getAttribute(
					SshShellSecurityAuthenticationProvider.AUTHENTICATION_ATTRIBUTE);
			SshAuthentication authentication = null;
			if (authenticationObject != null) {
				if (!(authenticationObject instanceof SshAuthentication)) {
					throw new IllegalStateException("Unknown authentication object class: " + authenticationObject.getClass().getName());
				}
				authentication = (SshAuthentication) authenticationObject;
			}

			SSH_THREAD_CONTEXT.set(new SshContext(sshThread, terminal, reader, authentication));
			shell.run(new SshShellInputProvider(reader, promptProvider));
			LOGGER.debug("{}: end", session.toString());
			quit(0);
		} catch (Throwable e) {
			LOGGER.error("{}: unexpected exception", session.toString(), e);
			quit(1);
		}
	}

	private void quit(int exitCode) {
		ec.onExit(exitCode);
	}

	@Override
	public void destroy(ChannelSession channelSession) throws Exception {
		// nothing to do
	}

	static class SshShellInputProvider
			extends InteractiveShellApplicationRunner.JLineInputProvider {

		public SshShellInputProvider(LineReader lineReader, PromptProvider promptProvider) {
			super(lineReader, promptProvider);
		}

		@Override
		public Input readInput() {
			SshContext ctx = SSH_THREAD_CONTEXT.get();
			if (ctx != null) {
				ctx.setPostProcessorsList(null);
			}
			try {
				return super.readInput();
			} catch (EndOfFileException e) {
				throw new ExitRequest(1);
			}
		}
	}

	@Override
	public void setErrorStream(OutputStream errOS) {
		// not used
	}

	@Override
	public void setExitCallback(ExitCallback ec) {
		this.ec = ec;
	}

	@Override
	public void setInputStream(InputStream is) {
		this.is = is;
	}

	@Override
	public void setOutputStream(OutputStream os) {
		this.os = os;
	}

	@Override
	public void setChannelSession(ChannelSession session) {
		this.session = session;
	}

	@Override
	public Command create() {
		return this;
	}
}
