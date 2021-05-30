#!/bin/bash
set -euo pipefail
set -x

java --add-exports ch.qos.logback.classic/ch.qos.logback.classic.model.processor=ch.qos.logback.core --module-path downloader\target\modules --module sh.xana.forum/sh.xana.forum.server.ServerMain "$@"