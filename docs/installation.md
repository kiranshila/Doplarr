# Installation

To use this bot, you have two options.

1. Use docker

This relies on a working docker setup, either standalone or as
part of a greater container orchestration system like portainer, unraid,
Synology NASs, etc. If you are already using one of these platforms, this is
the easiest path.

2. Run the JAR directly

Doplarr is written in the Clojure language, which runs on the Java Virtual
Machine (JVM). As such, the whole application is a single Java executable, a JAR
file. This requires far less setup than docker, but you the user are then
responsible for things like having the proper java version, setting the app up
to run on boot, etc. This might be the easier option on Windows as docker can
get a bit touchy on non-unix systems.

## Docker

Docker containers are being built on GitHub as part of the release cycle and are
hosted at `ghcr.io/kiranshila/doplarr:latest`. Several other communities like
[hotio](https://hotio.dev/) and [lsio](https://www.linuxserver.io/) have
custom containers to better fit Doplarr into their ecosystem.

The containers provided here are a small wrapper around a lightweight JVM
instance on Alpine linux and should work on any x86 host. ARM support _may_ be
provided by external bundlers.

!> The Doplarr team is not responsible for those containers or any Unraid templates they
provide.

As Doplarr is configured with either a config file or environment variables,
using the likes of Docker Compose with environment variables is probably the
easiest approach as then you don't have to mount a volume for configuration.
This is of course still an option, as long as you pass the `config` environment
variable along that points to the path in the container to the config file.

Without docker compose, you can invoke `docker run` with

```bash
docker run \
-e SONARR__URL='http://localhost:8989' \
-e RADARR__URL='http://localhost:7878' \
-e SONARR__API='sonarr_api' \
-e RADARR__API='radarr_api' \
-e DISCORD__TOKEN='bot_token' \
--name doplarr ghcr.io/kiranshila/doplarr:latest
```

Alternatively, with docker-compose:

```yaml
doplarr:
  environment:
    - SONARR__URL=http://localhost:8989
    - RADARR__URL=http://localhost:7878
    - SONARR__API=sonarr_api
    - RADARR__API=radarr_api
    - DISCORD__TOKEN=bot_token
  container_name: doplarr
  image: "ghcr.io/kiranshila/doplarr:latest"
```

For all options see the [configuration table](https://kiranshila.github.io/Doplarr/#/configuration?id=optional-settings)

## JAR File

To run with the jar file directly you need to either grab the jar and config
file from the [releases](https://github.com/kiranshila/Doplarr/releases/) or
build manually.

!> You need _both_ the JAR and the `config.edn` file

Next, fill out the config file with your configuration

Then run with:

```bash
java -jar Doplarr.jar -Dconfig=<path to config file>
```

!> Doplarr requires at _least_ Java 11. Many platforms use an old version of
Java by default and Doplarr will throw strange errors on startup. Check your
version with `java -version` to ensure you're up to date. If you're not, grab a
newer JDK from the likes of [adoptium](https://adoptium.net/)

### Building Manually

?> Only perform this step if you do not want to use the provided JAR file

1. Grab the latest [Clojure CLI
   Tools](https://clojure.org/guides/getting_started)
2. Clone this project
3. Run `clj -T:build uber`

### Running as a Service

If you are running the JAR directly, you need to orchestrate it's role as a
system service.

#### \*Nix Systems

On systemd based systems (most Linux unless you're a Guix nerd), this is easy
with a service file

```ini
[Unit]
Description=Doplarr Daemon
After=syslog.target network.target
[Service]
User=root
Group=root
Type=simple
WorkingDirectory=/opt/Doplarr
ExecStart=/usr/bin/java -jar target/Doplarr.jar -Dconfig=config.edn
TimeoutStopSec=20
KillMode=process
Restart=always
[Install]
WantedBy=multi-user.target
```

Make sure to modify `WorkingDirectory` to the path that you've copied the JAR
to. Also, ensure `ExecStart` points to your Java installation.

You probably want to customize the `User` and `Group` as well to match your
setup.

Then, as root:

- `systemctl -q daemon-reload`
- `systemctl enable --now -q doplarr`

#### Windows

The most obvious way to run the bare JAR on windows is to setup a batch file
with something like

```batch
@ECHO OFF
start java -jar Doplarr.jar -Dconfig=config.edn
```

This file, placed alongside the jar and the config file should work when run.
There are a million ways to run something on start in windows, so we'll leave
that one up to you.

Perhaps you may want to look into taking your freedom into consideration and
switch to an OS that respects your privacy and right to control your own hardware.
