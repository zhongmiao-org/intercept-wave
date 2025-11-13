This folder hosts a local test setup via docker-compose (upstream + console).

Usage
- From this folder:
  - `cd docker`
  - Copy `.env.example` to `.env` and adjust values
  - `docker compose -f docker-compose.client.yml up -d` (console + upstream)
  - or `docker compose -f docker-compose.upstream.yml up -d upstream` (upstream only)
- Open the console: `http://localhost:8080`
- Run the plugin inside your IDE, suggested ports:
  - HTTP: 8888(/api), 8889(/order-api), 8890(/pay-api)
  - WS: 8891, 8892, 8893
  - Upstream services are provided by the containers: HTTP 9000/9001/9002, WS 9003/9004/9005

Environment (.env)
- The console container reads runtime config from `.env` (via `env_file`). These URLs are used by the browser, so `localhost` points to your host machine where the plugin runs.
- See `.env.example` for defaults (localhost-based):
  - `HTTP_1`, `HTTP_2`, `HTTP_3`: point to your plugin’s HTTP endpoints
  - `WS_1`, `WS_2`, `WS_3`: point to your plugin’s WS endpoints
  - `WS_TOKEN`: token used by the console for WS connections

Notes
- If you really prefer `host.docker.internal`, keep in mind it’s a container-side DNS alias; browsers on the host typically don’t resolve it. Use `localhost` in `.env` for the browser to reach your plugin.
