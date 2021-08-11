# Doplarr

> A _Better_ Sonarr/Radarr Request Bot for Discord

## Running with Docker

Pull the latest image with `docker pull ghcr.io/kiranshila/doplarr:main`

Then run with `fixme`

## Building and Running Locally

You need the Clojure CLI tools to build

1. Clone directory
2. `clj -T:build uberjar`

### Configuring

1. Fill out `config.edn` with the requisite things

### Setting up as a service (On Linux (Systemd))

1. Create the file /etc/systemd/system/doplarr.service with the following

```ini
[Unit]
Description=Doplarr Daemon
After=syslog.target network.target
[Service]
User=root
Group=root
Type=simple
WorkingDirectory=/opt/Doplarr
ExecStart=/usr/bin/java -jar
TimeoutStopSec=20
KillMode=process
Restart=always
[Install]
WantedBy=multi-user.target
```

2. Customize the user, group, and working directory to the location of the jar

Then, as root

3. `systemctl -q daemon-reload`
4. `systemctl enable --now -q doplarr`
