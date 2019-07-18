#!/bin/bash

set -e

./scripts/check_java_home.sh

mvn test -Dtest=$1#$2 -Dmaven.surefire.debug