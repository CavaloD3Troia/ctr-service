COMPOSE := docker compose
APP_JAR := application/target/ctr-service-exec.jar

# Testcontainers ignores the docker CLI context, so with Colima, OrbStack or Rancher Desktop it may not
# find the daemon. Point it at the endpoint the CLI uses; inside those VMs the socket is /var/run/docker.sock.
ifeq ($(origin DOCKER_HOST),undefined)
DOCKER_CONTEXT_HOST := $(shell docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null)
ifneq ($(filter-out unix:///var/run/docker.sock,$(DOCKER_CONTEXT_HOST)),)
TESTCONTAINERS_ENV := DOCKER_HOST=$(DOCKER_CONTEXT_HOST) TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
endif
endif

.DEFAULT_GOAL := help

.PHONY: help
help: ## Show this help
	@grep -hE '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-15s\033[0m %s\n", $$1, $$2}'

# --- Build and test ---------------------------------------------------------------------------

.PHONY: build
build: ## Compile and run the unit and architecture tests
	./mvnw -B clean verify

.PHONY: integration-test
integration-test: ## Run the *IT integration tests (Postgres and Kafka in Testcontainers, needs Docker)
	$(TESTCONTAINERS_ENV) ./mvnw -B clean verify -Pintegration-tests

.PHONY: component-test
component-test: ## Run the Cucumber component tests (Postgres and Kafka in Testcontainers, needs Docker)
	$(TESTCONTAINERS_ENV) ./mvnw -B clean verify -Pcomponent-tests

.PHONY: package
package: ## Build the runnable jar, skipping the tests
	./mvnw -B package -DskipTests

# --- Run locally ------------------------------------------------------------------------------

.PHONY: deps-up
deps-up: ## Start only Postgres, Kafka and MinIO, then run or debug CtrApplication from the IDE
	$(COMPOSE) up -d --wait

.PHONY: up
up: package ## Build the app and start it in Docker together with Postgres and Kafka
	$(COMPOSE) --profile app up -d --build --wait

.PHONY: run
run: package ## Run the app on this machine, against `make deps-up`
	java -jar $(APP_JAR) --spring.profiles.active=dev

.PHONY: logs
logs: ## Follow the app logs
	$(COMPOSE) --profile app logs -f ctr-service

.PHONY: down
down: ## Stop everything, keeping the database
	$(COMPOSE) --profile app down

.PHONY: reset
reset: ## Stop everything and delete the database
	$(COMPOSE) --profile app down -v

.PHONY: clean
clean: ## Delete the build output
	./mvnw -B clean
