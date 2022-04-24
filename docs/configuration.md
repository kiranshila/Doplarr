# Configuration

## Discord

The first step in configuration is creating the bot in Discord itself.

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

!> Every user that you wish to have access to the slash commands needs to be assigned this role (even the server owner/admins).

This is optional and by default the bot will be accessible to everyone on the server.

## Sonarr/Radarr

All you need here are the API keys from `Settings->General`

## Overseerr

Sonarr/Radarr and Overseerr are mutually exclusive - you only need to configure
one. If you are using Overseerr, your users must have associated discord IDs, or
the request will fail.

As a note, this bot isn't meant to wrap the entirety of what Overseerr can do, just the
necessary bits for requesting with optional 4K and quota support. Just use the
web interface to Overseerr if you need more features.

## Optional Settings

| Environment Variable (Docker)  | Config File Keyword            | Type    | Default Value | Description                                                                                                                                 |
| ------------------------------ | ------------------------------ | ------- | ------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `DISCORD__MAX_RESULTS`         | `:discord/max-results`         | Integer | `25`          | Sets the maximum size of the search results selection                                                                                       |
| `DISCORD__ROLE_ID`             | `:discord/role-id`             | Long    | N/A           | The discord role id for users of the bot (omitting this lets everyone on the server use the bot)                                            |
| `DISCORD__REQUESTED_MSG_STYLE` | `:discord/requested-msg-style` | Keyword | `:plain`      | Sets the style of the request alert message. One of `:plain :embed :none`                                                                   |
| `SONARR__QUALITY_PROFILE`      | `:sonarr/quality-profile`      | String  | N/A           | The name of the quality profile to use by default for Sonarr                                                                                |
| `RADARR__QUALITY_PROFILE`      | `:radarr/quality-profile`      | String  | N/A           | The name of the quality profile to use by default for Radarr                                                                                |
| `SONARR__ROOTFOLDER`           | `:sonarr/rootfolder`           | String  | N/A           | The root folder to use by default for Sonarr                                                                                                |
| `RADARR__ROOTFOLDER`           | `:radarr/rootfolder`           | String  | N/A           | The root folder to use by default for Radarr                                                                                                |
| `SONARR__LANGUAGE_PROFILE`     | `:sonarr/language-profile`     | String  | N/A           | The name of the language profile to use by default for Sonarr                                                                               |
| `OVERSEERR__DEFAULT_ID`        | `:overseerr/default-id`        | Integer | N/A           | The Overseerr user id to use by default if there is no associated discord account for the requester                                         |
| `PARTIAL_SEASONS`              | `:partial-seasons`             | Boolean | `true`        | Sets whether users can request partial seasons.                                                                                             |
| `LOG_LEVEL`                    | `:log-level`                   | Keyword | `:info`       | The log level for the logging backend. This can be changed for debugging purposes. One of `:trace :debug :info :warn :error :fatal :report` |
