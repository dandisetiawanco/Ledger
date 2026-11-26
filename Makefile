.PHONY: setup dev stop test lint build seed-demo

setup:
	pnpm install
	cd backend && ./mvnw -B dependency:resolve

dev:
	./scripts/dev.sh

stop:
	./scripts/stop.sh

test:
	cd backend && ./mvnw -B test
	pnpm test:web

lint:
	pnpm typecheck:web
	pnpm lint:web

build:
	cd backend && ./mvnw -B package -DskipTests
	pnpm build:web

seed-demo:
	curl -fsS -X POST http://127.0.0.1:8082/api/v1/demo/start >/dev/null && echo "Demo workspace ready at http://localhost:3002/demo"
