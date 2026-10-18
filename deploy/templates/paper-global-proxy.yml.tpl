# Seeded by SULD deploy.sh (AUTH_MODE=hybrid) before Paper's first boot; Paper adds every other key itself.
# The secret is the proxy's forwarding.secret: only that Velocity can hand players to this server.
_version: 31
proxies:
  velocity:
    enabled: true
    online-mode: false
    secret: '@VELOCITY_FORWARDING_SECRET@'
