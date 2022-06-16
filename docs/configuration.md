# Configuration

## Discord

The first step in configuration is creating the bot in Discord itself.

1. Create a new [Application](https://discord.com/developers/applications) in Discord
2. Go to the Bot tab and add a new bot
3. Copy out the token
4. Go to OAuth2 and under "OAuth2 URL Generator", enable `applications.commands` and `bot`
5. Copy the resulting URL and use as the invite link to your server

### Permissions

As of Doplarr v3.5.0, we removed the ability to role-gate the bot via our
configuration file as Discord launched the command permissions system within the client itself.

To access this, after adding the bot to your server, navigate to `Server Settings -> Integrations -> Doplarr (or whatever you named it) -> Manage` and
from there you can configure the channels for which the bot is active and who
has access to the bot. This is a lot more powerful than the previous system and
users of the previous `ROLE_ID`-based approach must update as Discord broke the
old system.

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
|--------------------------------|--------------------------------|---------|---------------|---------------------------------------------------------------------------------------------------------------------------------------------|
| `DISCORD__MAX_RESULTS`         | `:discord/max-results`         | Integer | `25`          | Sets the maximum size of the search results selection                                                                                       |
| `DISCORD__REQUESTED_MSG_STYLE` | `:discord/requested-msg-style` | Keyword | `:plain`      | Sets the style of the request alert message. One of `:plain :embed :none`                                                                   |
| `SONARR__QUALITY_PROFILE`      | `:sonarr/quality-profile`      | String  | N/A           | The name of the quality profile to use by default for Sonarr                                                                                |
| `RADARR__QUALITY_PROFILE`      | `:radarr/quality-profile`      | String  | N/A           | The name of the quality profile to use by default for Radarr                                                                                |
| `SONARR__ROOTFOLDER`           | `:sonarr/rootfolder`           | String  | N/A           | The root folder to use by default for Sonarr                                                                                                |
| `RADARR__ROOTFOLDER`           | `:radarr/rootfolder`           | String  | N/A           | The root folder to use by default for Radarr                                                                                                |
| `SONARR__SEASON_FOLDERS`       | `:sonarr/season-folders`       | Boolean | `false`       | Sets whether you're using season folders in Sonarr                                                                                                                                            |
| `SONARR__LANGUAGE_PROFILE`     | `:sonarr/language-profile`     | String  | N/A           | The name of the language profile to use by default for Sonarr                                                                               |
| `OVERSEERR__DEFAULT_ID`        | `:overseerr/default-id`        | Integer | N/A           | The Overseerr user id to use by default if there is no associated discord account for the requester                                         |
| `PARTIAL_SEASONS`              | `:partial-seasons`             | Boolean | `true`        | Sets whether users can request partial seasons.                                                                                             |
| `LOG_LEVEL`                    | `:log-level`                   | Keyword | `:info`       | The log level for the logging backend. This can be changed for debugging purposes. One of `:trace :debug :info :warn :error :fatal :report` |
