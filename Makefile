.PHONY: install build test verify-artifacts test-example-1 setup-monorepo update-monorepo setup-golang-sdk update-golang-sdk setup-references update-references

install:
	mvn install

build:
	mvn compile package

test:
	mvn test

verify-artifacts:
	mvn package
	bash scripts/verify-artifacts.sh

test-example-1:
	mvn test
	mvn -pl featurevisor-sdk exec:java -Dexec.mainClass="com.featurevisor.cli.CLI" -Dexec.args="test --projectDirectoryPath=../featurevisor/examples/example-1 --onlyFailures"

##
# Monorepo
#
setup-monorepo:
	mkdir -p monorepo
	if [ ! -d "monorepo/.git" ]; then \
		git clone git@github.com:featurevisor/featurevisor.git monorepo; \
	else \
		(cd monorepo && git fetch origin main && git checkout main && git pull origin main); \
	fi
	make update-monorepo

update-monorepo:
	(cd monorepo && git pull origin main && make install && make build)

##
# Golang SDK
#
setup-golang-sdk:
	mkdir -p featurevisor-go
	if [ ! -d "featurevisor-go/.git" ]; then \
		git clone git@github.com:featurevisor/featurevisor-go.git featurevisor-go; \
	else \
		(cd featurevisor-go && git fetch origin main && git checkout main && git pull origin main); \
	fi

update-golang-sdk:
	(cd featurevisor-go && git pull origin main)

##
# All references
#
setup-references:
	make setup-monorepo
	make setup-golang-sdk

update-references:
	make update-monorepo
	make update-golang-sdk
