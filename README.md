# Spring Boot Ssh Shell

[![Build Status](https://travis-ci.org/fonimus/ssh-shell-spring-boot.svg?branch=master)](https://travis-ci.org/fonimus/ssh-shell-spring-boot)
[![Code Quality](https://api.codacy.com/project/badge/Grade/e695bc79f42c4c80a58f78ebef8c632b)](https://www.codacy.com/app/francois.onimus/ssh-shell-spring-boot?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=fonimus/ssh-shell-spring-boot&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/e695bc79f42c4c80a58f78ebef8c632b)](https://www.codacy.com/app/francois.onimus/ssh-shell-spring-boot?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=fonimus/ssh-shell-spring-boot&amp;utm_campaign=Badge_Coverage)
[![Maven Central](https://img.shields.io/maven-central/v/com.github.fonimus/ssh-shell-spring-boot-starter.svg?label=%22maven%20central%22)](https://search.maven.org/search?q=g:%22com.github.fonimus%22%20AND%20a:%22ssh-shell-spring-boot-starter%22)

> Spring shell in spring boot application over ssh


For more information please visit `spring shell` [website](https://projects.spring.io/spring-shell/) 
or [2.0.1 reference documentation](https://docs.spring.io/spring-shell/docs/2.0.1.RELEASE/reference/htmlsingle/).

* [Getting started](#getting-started)
* [Actuator commands](#actuator-commands)
* [Post processors](#post-processors)
* [Parameter providers](#parameter-providers)
* [Custom authentication](#custom-authentication)
* [Command helper](#command-helper)
* [Banner](#banner)
* [Tests](#tests)
* [Samples](#samples)
* [Release notes](#release-notes)


## Getting started

### Dependency

```xml
<dependency>
    <groupId>com.github.fonimus</groupId>
    <artifactId>ssh-shell-spring-boot-starter</artifactId>
</dependency>
```

**Note:** auto configuration `SshShellAutoConfiguration` (active by default) can be deactivated by property **ssh.shell.enable=false**.

It means that the ssh server won't start and the commands won't be scanned. Unfortunately the application will still
load the `spring-shell` auto configuration classes and display a shell at startup (shell:>). You can disable them with
following property:

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.shell.jline.JLineShellAutoConfiguration
      - org.springframework.shell.SpringShellAutoConfiguration
      - org.springframework.shell.jcommander.JCommanderParameterResolverAutoConfiguration
      - org.springframework.shell.legacy.LegacyAdapterAutoConfiguration
      - org.springframework.shell.standard.StandardAPIAutoConfiguration
      - org.springframework.shell.standard.commands.StandardCommandsAutoConfiguration
```  

### Configuration

Please check class: [SshShellProperties.java](./starter/src/main/java/com/github/fonimus/ssh/shell/SshShellProperties.java) for more information

```yaml
ssh:
  shell:
    enable: true
    actuator:
      enable: true
      # empty by default
      excludes:
      - ...
    # 'simple' or 'security'
    authentication: simple
    # if authentication set to 'security' the AuthenticationProvider bean name
    # if not specified and only one AuthenticationProvider bean is present in the context, it will be used 
    auth-provider-bean-name:
    # for ssh helper 'confirm' method
    confirmation-words:
    - y    
    - yes
    # set to false to disable following default built-in commands
    default-commands:
      jvm: true
      postprocessors: true
      thread: true
    display-banner: true
    # to use AnyOsFileValueProvider instead of spring shell FileValueProvider for all File option parameters
    # if set to false, it still can be used via '@ShellOption(valueProvider = AnyOsFileValueProvider.class) File file'
    any-os-file-provider: true
    history-file: <java.io.tmpdir>/sshShellHistory.log
    host: 127.0.0.1
    host-key-file: <java.io.tmpdir>/hostKey.ser
    # displayed in log if generated
    password:
    port: 2222
    user: user
    prompt:
      # in enum: com.github.fonimus.ssh.shell.PromptColor (black, red, green, yellow, blue, magenta, cyan, white, bright)
      color: white
      text: 'shell>'
```

* Add `spring-boot-starter-actuator` dependency to get actuator commands

* Add `spring-boot-starter-security` dependency to configure `ssh.shell.authentication=security` with *AuthenticationProvider*

### Writing commands

You can write your command exactly the way you would do with `spring shell` (For more information please visit `spring shell` 
([website](https://projects.spring.io/spring-shell/), [reference documentation](https://docs.spring.io/spring-shell/docs/2.0.0.RELEASE/reference/htmlsingle/))

Instead of using `org.springframework.shell.standard.ShellComponent` annotation, you should use `com.github.fonimus.ssh.shell.commands.SshShellComponent`: 
it is just a conditional `@ShellComponent` with `@ConditionalOnProperty` on property **ssh.shell.enable** 

Example:

```java
import org.springframework.shell.standard.ShellCommandGroup;
import org.springframework.shell.standard.ShellMethod;

import com.github.fonimus.ssh.shell.commands.SshShellComponent;


@SshShellComponent
@ShellCommandGroup("Test Commands")
public class TestCommands {

	@ShellMethod("test command")	
	public String test() {
	  return "ok";
	}
}
``` 

## Actuator commands

If `org.springframework.boot:spring-boot-starter-actuator` dependency is present, actuator commands
will be available. 

Command availability is binded to endpoint activation.

```yaml
management:
  endpoint:
    audit:
      enabled: false
```

It can also be deactivated by putting command name is exclusion list.

```yaml
ssh:
  shell:
    actuator:
      excludes:
      - audit
      - ...
``` 

## Post processors

Post processors can be used with '|' (pipe character) followed by the name of the post processor and the parameters.
Also, custom ones can be added.

### Provided post processors

#### Save

This specific post processor takes the key character '>'.

Example: ```echo test > /path/to/file.txt```

#### Pretty

This post processor, named `pretty` takes an object and apply jackson pretty writer.

Example: ```info | pretty```

#### Json

This post processor, named `json` allows you to find a specific path within a json object.

Caution: you need to have a json string. You can apply `pretty` post processor before to do so.

Example: ```info | pretty | json /build/version```

#### Grep

This post processor, named `grep` allows you to find specific patterns within a string.

Examples: ```info | grep boot```,```info | pretty | grep boot spring```

#### Highlight

This post processor, named `highlight` allows you to highlight specific patterns within a string.

Examples: ```info | highlight boot```,```info | pretty | highlight boot spring```

### Custom

To register a new json result post processor, you need to implement interface `PostProcessor`

Then register it within a spring configuration.

Example:

````java
@Bean
public PostProcessor quotePostProcessor() {
    return new PostProcessor<String>() {

        @Override
        public String getName() {
            return "quote";
        }

        @Override
        public String process(String result, List parameters) {
            return "'" + result + "'";
        }
    };
}
````

## Parameter providers

### Enum

Enumeration option parameters have auto completion by default.

### File

Thanks to [AnyOsFileValueProvider.java](./starter/src/main/java/com/github/fonimus/ssh/shell/providers/AnyOsFileValueProvider.java) 
(or FileValueProvider is deactivated), auto completion is available
for `java.io.File` option parameters.

### Custom

To enable auto completion for a parameter, declare a **valueProvider** class.

**Note:**  the value provider has to be in the spring context.

````java

...
@ShellOption(valueProvider = CustomValuesProvider.class) String message
...

@Component
class CustomValuesProvider
        extends ValueProviderSupport {

    private final static String[] VALUES = new String[]{
            "message1", "message2", "message3"
    };

    @Override
    public List<CompletionProposal> complete(MethodParameter parameter, CompletionContext completionContext, String[] hints) {
        return Arrays.stream(VALUES).map(CompletionProposal::new).collect(Collectors.toList());
    }
}
````

## Custom authentication

Instead of setting user and password (or using generated one), you can implement your own `SshShellAuthenticationProvider`.

Auto configuration will create default implementation only if there is not an existing one in the spring context.

Example:

```java
import com.github.fonimus.ssh.shell.SshShellAuthenticationProvider;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CustomPasswordConfiguration {

    @Bean
    public SshShellAuthenticationProvider sshShellAuthenticationProvider() {
        return (user, pass, serverSession) -> user.equals(pass);
    }

}
```

## Command helper

A `com.github.fonimus.ssh.shell.SshShellHelper` bean is provided in context to help for additional functionalities.

You can either autowire it or inject it in constructor:

```java
import com.github.fonimus.ssh.shell.SshShellHelper;

@SshShellComponent
public class DemoCommand {
	
	@Autowired
	private SshShellHelper helper;
	
	// or
	
	public DemoCommand(SshShellHelper helper){
		this.helper = helper;
	}
}
```

### User interaction

#### Print output

```java
@SshShellComponent
public class DemoCommand {
	
	@Autowired
	private SshShellHelper helper;

	@ShellMethod("Print command")	
	public String print() {
	    boolean success = ...;
	    helper.print("Some message");
	    helper.print("Some black message", PromptColor.BLACK);
	    helper.printSuccess("Some success message");
	    return success ? helper.getSuccess("Some returned success message") : helper.getColored("Some returned blue message", PromptColor.BLUE);
	}
}
```

#### Read input

```java
@SshShellComponent
public class DemoCommand {
	
	@Autowired
	private SshShellHelper helper;

	@ShellMethod("Welcome command")	
	public String welcome() {
	    String name = helper.read("What's your name ?");
	    return "Hello, '" + name + "' !";
	}
}
```

#### Confirmation

Util `confirm` method displays confirmation message and returns `true` 
if response equals ignore case confirmation words.

Default confirmation words are **[`y`, `yes`]**:

You can specify if it is case sensitive and provide your own confirmation words.

```java
@SshShellComponent
public class DemoCommand {
	
	@Autowired
	private SshShellHelper helper;

	@ShellMethod("Confirmation command")
	public String conf() {
	    return helper.confirm("Are you sure ?" [, true|false] [, "oui", "si", ...]) ? "Great ! Let's do it !" : "Such a shame ...";
	}
}
```

### Interactive

This method takes an interface to display lines at regular interval.

Every **refresh delay** (here 2 seconds), `com.github.fonimus.ssh.shell.interactive.InteractiveInput.getLines` is called.

This can be used to display progress, monitoring, etc.

The interactive builder, [Interactive.java](./starter/src/main/java/com/github/fonimus/ssh/shell/interactive/Interactive.java) 
allows you to build your interactive command.

This builder can also take key bindings to make specific actions, whose can be made by the following builder: 
[KeyBinding.java](./starter/src/main/java/com/github/fonimus/ssh/shell/interactive/KeyBinding.java).

```java
@SshShellComponent
public class DemoCommand {
	
	@Autowired
	private SshShellHelper helper;
	
	@ShellMethod("Interactive command")
	public void interactive() {
	    
        KeyBinding binding = KeyBinding.builder()
                .description("K binding example")
                .key("k").input(() -> LOGGER.info("In specific action triggered by key 'k' !")).build();

        Interactive interactive = Interactive.builder().input((size, currentDelay) -> {
            LOGGER.info("In interactive command for input...");
            List<AttributedString> lines = new ArrayList<>();
            AttributedStringBuilder sb = new AttributedStringBuilder(size.getColumns());

            sb.append("\nCurrent time", AttributedStyle.BOLD).append(" : ");
            sb.append(String.format("%8tT", new Date()));

            lines.add(sb.toAttributedString());

            SecureRandom sr = new SecureRandom();
            lines.add(new AttributedStringBuilder().append(helper.progress(sr.nextInt(100)),
                    AttributedStyle.DEFAULT.foreground(sr.nextInt(6) + 1)).toAttributedString());
            lines.add(AttributedString.fromAnsi(SshShellHelper.INTERACTIVE_LONG_MESSAGE + "\n"));

            return lines;
        }).binding(binding).fullScreen(true|false).refreshDelay(5000).build();

        helper.interactive(interactive);
	}
}
```

Note: existing key bindings are:

* `q`: to quit interactive command and go back to shell
* `+`: to increase refresh delay by 1000 milliseconds
* `-`: to decrease refresh delay by 1000 milliseconds

### Role check

If you are using *AuthenticationProvider* thanks to property `ssh.shell.authentication=security`, you can check that connected user has right authorities for command.
The easiest way of doing it is thanks to `ShellMethodAvailability` functionality. Example:

```java
@SshShellComponent
public class DemoCommand {
	
	@Autowired
	private SshShellHelper helper;

	@ShellMethod("Admin command")
	@ShellMethodAvailability("adminAvailability")
	public String admin() {
		return "Finally an administrator !!";
	}

	public Availability adminAvailability() {
		if (!helper.checkAuthorities(Collections.singletonList("ADMIN"))) {
			return Availability.unavailable("admin command is only for an admin users !");
		}
		return Availability.available();
	}
}
```

### Retrieve spring security authentication


```java
@SshShellComponent
public class DemoCommand {
	
	@Autowired
	private SshShellHelper helper;
	
	@ShellMethod("Authentication command")
	public SshAuthentication authentication() {
		return helper.getAuthentication();
	}
}
```

## Banner

If a banner is found in spring context and `display-banner` is set to true, 
it will be used as welcome prompt message.

## Tests

It can be annoying to load ssh server during spring boot tests.
`SshShellProperties` class provides constants to easily deactivate 
the all ssh and spring shell auto configurations: 

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = { "ssh.shell.port=2346",
		SshShellProperties.DISABLE_SSH_SHELL,
		SshShellProperties.DISABLE_SPRING_SHELL_AUTO_CONFIG
})
@ExtendWith(SpringExtension.class)
public class ApplicationTest {}
```

## Samples

* [Basic sample](./samples/basic), no actuator, no security, no sessions

* [Complete sample](./samples/complete), actuator, security dependencies and configurations


## Release notes

### 1.1.7

* Bump to spring boot 2.1.8.RELEASE

### 1.1.6

* Bump to spring boot 2.1.7.RELEASE
* Bump to sshd 2.3.0
* Add properties to exclude not wanted built-in commands
    * Via properties `ssh.shell.default-commands.*`

### 1.1.5

* Add `threads`, `jvm-env` and `jvm-properties` built-it commands

### 1.1.4

* [AnyOsFileValueProvider.java](./starter/src/main/java/com/github/fonimus/ssh/shell/providers/AnyOsFileValueProvider.java)
replaces `FileValueProvider` (spring shell default) by default 
    * Supports Windows OS in addition to Unix
    * Can be deactivated by `ssh.shell.any-os-file-provider`

### 1.1.3

* Remove `static` from SshShellHelper methods (**getColored**, **getBackgroundColored**)
* Add methods in SshShellHelper
    * `terminalSize` to get terminal columns and rows capabilities
    * `progress` to fill line with progress bar
        * `[========> ]`
    * `interactive` which takes an interface to display lines at regular interval
        * Check complete sample for a demo

### 1.1.2

* Fix option arguments with spaces, quotes, etc 
* Update to `spring boot 2.1.3`
* Update to `javadoc plugin 3.1.0`
* Update to `sshd 2.2.0`

### 1.1.1

* Update to `spring boot 2.1.0`
    * Avoid overriding bean definitions as it is now disabled by default

### 1.1.0

* **New artifact identifier: ssh-shell-spring-boot-starter** -> [maven central link](https://search.maven.org/search?q=g:%22com.github.fonimus%22%20AND%20a:%22ssh-shell-spring-boot-starter%22)

### 1.0.6

* Add new post processor
    * `highlight`
* Fix various issues where ssh.shell.enable=false makes application fail to start
* Add new `@SshShellComponent`

### 1.0.5

* Add post processor feature
    * `pretty`
    * `grep`
    * `json`
    * `save`
* Fix issue with terminal size which made autocomplete fail

### 1.0.4

* Make PrettyJsonResultHandler bean conditional on jackson ObjectMapper class
* Fix application start issue if banner is with configured with off mode

### 1.0.3

* Fix not working history
* Make history file location configurable
* Add authentication objects in ssh session context

### 1.0.2

* Add more user interactions in SshShellHelper

### 1.0.1

* Add spring security authentication provider for ssh connection
* Add spring security roles check
* Add user interactions (read, confirm)
* Bug fixes

### 1.0.0

* First stable release
