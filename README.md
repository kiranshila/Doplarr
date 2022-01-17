<p align="center">
  <img src="https://raw.githubusercontent.com/kiranshila/Doplarr/main/logos/logo_title.png" width="350">
</p>

</p>
<p align="center">
<img alt="GitHub Workflow Status" src="https://img.shields.io/github/workflow/status/kiranshila/doplarr/Main?style=for-the-badge">
<img alt="Lines of code" src="https://img.shields.io/tokei/lines/github/kiranshila/doplarr?style=for-the-badge">
<img alt="Discord" src="https://img.shields.io/discord/890634173751119882?color=ff69b4&label=discord&style=for-the-badge">
<img alt="Code" src="https://img.shields.io/badge/code-data-blueviolet?style=for-the-badge">
</p>

> An \*arr Request Bot for Discord

## Why does this exist

- Uses modern Discord slash commands and components, which provides a clean, performant UI on desktop and mobile.
  This has the added benefit of not requiring privileged intents, so this bot will _never_ look at message content
- Small codebase as [code is not an asset](https://robinbb.com/blog/code-is-not-an-asset/)
- Simple configuration, no need to have a whole web frontend just for configuration
- Powered by Clojure and [Discljord](https://github.com/IGJoshua/discljord), a markedly good combination 😛

### Caveats

I wanted a clean app for the sole purpose of requesting movies/TV shows.
If you need Ombi support (for managing many people requesting), I suggest you check out Overseerr instead.
There is only a boolean permission (role gated) for who has access to the bot, nothing fancy.

### Screenshots

<img src="https://raw.githubusercontent.com/kiranshila/Doplarr/main/screenshots/Request.png" width="400">
<img src="https://raw.githubusercontent.com/kiranshila/Doplarr/main/screenshots/Selection.png" width="400">
<img src="https://raw.githubusercontent.com/kiranshila/Doplarr/main/screenshots/button.png" width="400">

### FAQ

#### Will you support Lidarr/Readarr/\*arr

Soon™

#### Why are the commands greyed out?

If you are using role-gating, due to how slash command permissions work in Discord, every user that intends to
use the bot must have the assigned role you created. That _includes_ the server
owner/admins. Make sure that you assigned the role to yourself and the role ID
you copied is correct.

#### Do you have a support server?

Yes! [here](https://discord.gg/884mGq2fV6)

#### This is super useful! Can I buy you a beer?

Yes, you're too kind! There is a Github sponsor link off to the side.

## Setup

Please read all of this setup carefully. Most problems in deployment come from missing a step.

### Java

If you are running without Docker, you need to have at least Java 11 installed, such as [adoptium](https://adoptium.net/)

### Discord

1. Create a new [Application](https://discord.com/developers/applications) in Discord
2. Go to the Bot tab and add a new bot
3. Copy out the token
4. Go to OAuth2 and under "OAuth2 URL Generator", enable `applications.commands` and `bot`
5. Copy the resulting URL and use as the invite link to your server

In the server for which you will use the bot, you have the option to restrict
the commands to a certain role. For this, you need to create a new role for
your users (or use an existing one). Then, grab that role id.

To do this:

1. Enable Developer Mode (User Settings -> Advanced -> Developer Mode)
2. Under your server settings, go to Roles, find the role and "Copy ID"

Every user that you wish to have access to the slash commands needs to be
assigned this role (even the server owner/admins).

This is optional and by default the bot will be accessible to everyone on the server.

### Sonarr/Radarr

1. Copy out your API keys from Settings -> General

### Overseerr

Sonarr/Radarr and Overseerr are mutually exclusive - you only need to configure
one. If you are using Overseerr, your users must have associated discord IDs, or
the request will fail.

This bot isn't meant to wrap the entirety of what Overseerr can do, just the
necessary bits for requesting with optional 4K and quota support. Just use the
web interface to Overseerr if you need more features.

In the config, you replace `SONARR__URL`, `SONARR__API`, `RADARR__URL`,
`RADARR__API` with `OVERSEERR__URL` and `OVERSEERR__API`.

## Running with Docker

Simply run with

```bash
docker run \
-e SONARR__URL='http://localhost:8989' \
-e RADARR__URL='http://localhost:7878' \
-e SONARR__API='sonarr_api' \
-e RADARR__API='radarr_api' \
-e DISCORD__TOKEN='bot_token' \
--name doplarr ghcr.io/kiranshila/doplarr:latest
```

Alternatively, use docker-compose:

```yaml
doplarr:
  environment:
    - SONARR__URL='http://localhost:8989’
    - RADARR__URL='http://localhost:7878’
    - SONARR__API=sonarr_api
    - RADARR__API=radarr_api
    - DISCORD__TOKEN=bot_token
  container_name: doplarr
  image: ‘ghcr.io/kiranshila/doplarr:latest’
```

## Building and Running Locally

You need the Clojure CLI tools to build

1. Clone directory
2. `clj -T:build uber`

To skip the build, just download `Doplarr.jar` and `config.edn` from the releases

## Configuring

1. Fill out `config.edn` with the requisite things

### Optional Settings

| Environment Variable (Docker)  | Config File Keyword            | Type    | Default Value | Description                                                                                                                                |
| ------------------------------ | ------------------------------ | ------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `DISCORD__MAX_RESULTS`         | `:discord/max-results`         | Integer | `25`          | Sets the maximum size of the search results selection                                                                                      |
| `DISCORD__ROLE_ID`             | `:discord/role-id`             | Long    | N/A           | The discord role id for users of the bot (omitting this lets everyone on the server use the bot)                                           |
| `DISCORD__REQUESTED_MSG_STYLE` | `:discord/requested-msg-style` | Keyword | `:plain`      | Sets the style of the request alert message. One of `:plain :embed :none`                                                                  |
| `SONARR__QUALITY_PROFILE`      | `:sonarr/quality-profile`      | String  | N/A           | The name of the quality profile to use by default for Sonarr                                                                               |
| `RADARR__QUALITY_PROFILE`      | `:radarr/quality-profile`      | String  | N/A           | The name of the quality profile to use by default for Radarr                                                                               |
| `SONARR__LANGUAGE_PROFILE`     | `:sonarr/language-profile`     | String  | N/A           | The name of the language profile to use by default for Radarr                                                                              |
| `OVERSEERR__DEFAULT_ID`        | `:overseerr/default-id`        | Integer | N/A           | The Overseerr user id to use by default if there is no associated discord account for the requester                                        |
| `PARTIAL_SEASONS`              | `:partial-seasons`             | Boolean | `true`        | Sets whether users can request partial seasons.                                                                                            |
| `LOG_LEVEL`                    | `:log-level`                   | Keyword | `:info`       | The log level for the logging backend. This can be changed for debugging purposes. One of `trace :debug :info :warn :error :fatal :report` |

### Setting up on Windows

1. Make a folder called Doplarr that contains the jar, the config file, and the
   following batch file (something like `run.bat`)

```batchfile
@ECHO OFF
start java -jar Doplarr.jar -Dconfig=config.edn
```

### Setting up on Linux (as a systemd service)

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
ExecStart=/usr/bin/java -jar target/Doplarr.jar -Dconfig=config.edn
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
