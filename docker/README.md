This folder contains a local docker-compose setup for testing the plugin against upstream services and a browser console.

## Usage
- From this folder:
  - `cd docker`
  - Copy `.env.example` to `.env` and adjust values
  - Use `config.multi-route.example.json` as a ready-made `4.0` multi-route config that targets upstream ports `9000/9001/9002`
  - `docker compose -f docker-compose.client.yml up -d` (console + upstream)
  - or `docker compose -f docker-compose.upstream.yml up -d upstream` (upstream only)
- Open the console: `http://localhost:8080`
- Run the plugin inside your IDE with the following suggested ports:
  - HTTP: 8888(/api), 8889(/order-api), 8890(/pay-api)
  - WS: 8891, 8892, 8893
  - Upstream services are provided by the containers: HTTP 9000/9001/9002, WS 9003/9004/9005

## Environment (`.env`)
- The console container reads runtime config from `.env` via `env_file`. These URLs are used by the browser, so `localhost` should point to your host machine, where the plugin is running.
- See `.env.example` for defaults (localhost-based):
  - `HTTP_1`, `HTTP_2`, `HTTP_3`: point to your plugin’s HTTP endpoints
  - `WS_1`, `WS_2`, `WS_3`: point to your plugin’s WS endpoints
  - `WS_TOKEN`: token used by the console for WS connections

## Notes
- If you prefer `host.docker.internal`, remember that it is mainly a container-side DNS alias. Browsers running on the host usually cannot resolve it, so `localhost` is the safer choice in `.env`.
