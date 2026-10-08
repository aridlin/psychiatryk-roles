# Shared music connection capacity

The previous service admitted sixteen HTTP connections in total and silently
closed additional connections. A cached WAV listener can occupy a connection
for the duration of playback, so simultaneous listeners could prevent another
player from opening or seeking a song.

The replacement keeps admission bounded: 160 total connections, 128 audio
streams and 16 separate API requests. Audio saturation leaves API capacity
available. Saturation returns HTTP 503 with Retry-After instead of dropping the
socket. Authentication, cached media, range bytes, import workers and storage
policies remain intact. No additional media limits are introduced.

`python test_capacity.py` runs the actual HTTP handler on localhost. Its 154
checks include 32 simultaneous authenticated held streams, usable API responses
during audio saturation, exact range bytes, authorization, pool release and
explicit overload responses. No game or external service is needed.

The private operator validates the previously reviewed release and preserves
the old systemd drop-in for rollback. It refuses activation before
2026-10-08 15:37:27 UTC, the user-authorized two-minute window. Staging does not restart a service.
The earlier 04:00-only activation plan was superseded by explicit user steering.
The reviewed deployment uses the existing authenticated Caddy route.

This is separate from the client patch: clients also retry transient failures
with backoff and issue one warning per playback identity. Multiplayer playback
on other computers still requires live confirmation after activation.
