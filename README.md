<p align="center">
  <img src="https://raw.githubusercontent.com/kiranshila/Doplarr/main/logos/logo_cropped.png" width="200">
</p>

</p>
<p align="center">
<img alt="GitHub Workflow Status" src="https://img.shields.io/github/workflow/status/kiranshila/doplarr/Main?style=for-the-badge">
<img alt="Lines of code" src="https://img.shields.io/tokei/lines/github/kiranshila/doplarr?style=for-the-badge">
<img alt="Code" src="https://img.shields.io/badge/code-data-blueviolet?style=for-the-badge">
<img alt="Discord" src="https://img.shields.io/discord/890634173751119882?color=ff69b4&label=discord&style=for-the-badge">
</p>

> A _Better_ Sonarr/Radarr Request Bot for Discord

## Why does this exist

- Uses modern Discord slash commands and components, which provides a clean, performant UI on desktop and mobile
  - This has the added benifit of not requiring privileged intents, so this bot will _never_ look at message content
- Simple codebase, <1k lines of code which makes it easier to maintain. [Code is not an asset](https://robinbb.com/blog/code-is-not-an-asset/)
- Simple configuration, no need to have a whole web frontend just for configuration
- Powered by Clojure and [Discljord](https://github.com/IGJoshua/discljord), a markedly good combination 😛

### Caveats

I wanted a clean app for the sole purpose of requesting movies/TV shows.
I personally didn't need Siri integration, support for old API versions, Ombi,
etc., so those features are missing here.
If you need Ombi support (for managing many people requesting), I suggest you check out Overseerr instead.
There is only a boolean permission (role gated) for who has access to the bot, nothing fancy.

If any of these don't suit your fancy, check out
[Requestrr](https://github.com/darkalfx/requestrr)

### Screenshots

<img src="https://raw.githubusercontent.com/kiranshila/Doplarr/main/screenshots/Request.png" width="400">
<img src="https://raw.githubusercontent.com/kiranshila/Doplarr/main/screenshots/Selection.png" width="400">
<img src="https://raw.githubusercontent.com/kiranshila/Doplarr/main/screenshots/button.png" width="400">

### FAQ

#### Will you support Lidarr/Readarr/\*arr

Not yet. The idea is that one can work directly with the collection managers or
work through a request manager (Overseerr). As Overseerr doesn't support
collections managers other than radarr/sonarr and I want feature-parity, those
other managers will be left out until Overseerr supports them.

#### Why are the commands greyed out?

If you are using role-gating, due to how slash command permissions work in Discord, every user that intends to
use the bot must have the assigned role you created. That _includes_ the server
owner/admins. Make sure that you assigned the role to yourself and the role ID
you copied is correct.

#### Do you have a support server?

Yes! [here](https://discord.com/channels/890634173751119882/890634174195707965/890641954734485605)

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

In the config, you replace `SONARR_URL`, `SONARR_API`, `RADARR_URL`,
`RADARR_API` with `OVERSEERR_URL` and `OVERSEERR_API`.

## Running with Docker

Simply run with

```bash
docker run \
-e SONARR_URL='http://localhost:8989' \
-e RADARR_URL='http://localhost:7878' \
-e SONARR_API='sonarr_api' \
-e RADARR_API='radarr_api' \
-e BOT_TOKEN='bot_token' \
--name doplarr ghcr.io/kiranshila/doplarr:latest
```

Alternatively, use docker-compose:

```yaml
doplarr:
    environment:
        - ‘SONARR_URL=http://localhost:8989’
        - ‘RADARR_URL=http://localhost:7878’
        - SONARR_API=sonarr_api
        - RADARR_API=radarr_api
        - BOT_TOKEN=bot_token
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

| Environment Variable (Docker) | Config File Keyword | Type    | Description                                                                                      |
| ----------------------------- | ------------------- | ------- | ------------------------------------------------------------------------------------------------ |
| `MAX_RESULTS`                 | `:max-results`      | Integer | Sets the maximum size of the search results selection                                            |
| `ROLE_ID`                     | `:role-id`          | String  | The discord role id for users of the bot (omitting this lets everyone on the server use the bot) |
| `PARTIAL_SEASONS`             | `:partial-seasons`  | Boolean | Sets whether users can request partial seasons. Defaults to true or setting in Overseer          |

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
