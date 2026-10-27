# Prayer times feature (merged into the launcher)

Prayer times and weather are now built into the CleanYourTV launcher. The launcher owns the settings screen,
location and calculation-method preferences, prayer alerts, and the home-screen cards. It no longer needs to
install or query a separate prayer-times APK.

Open **Launcher Settings → Prayer times and weather** to configure the city, coordinates, calculation method,
and optional reminder. The feature uses Aladhan for prayer times and Open-Meteo for weather. It sends the date,
configured coordinates, calculation method, and device time zone to those services; it does not request location
permission.

This directory is retained as legacy standalone source for reference only. The GitHub release workflow no longer
builds or publishes it. New work should go in the launcher module under `launcher/app`.
