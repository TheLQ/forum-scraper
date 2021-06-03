#!/bin/bash
set -euo pipefail
set -x

aws s3 cp s3://xana.sh/config-client.properties .

xauthkey=$(grep server.auth config-client.properties | cut -d'=' -f2 | tr -d '[:space:]')
server=$(grep server.address config-client.properties | cut -d'=' -f2 | tr -d '[:space:]')

wget -N --header "x-xana-auth: $xauthkey" $server/file/dependencies.jar
wget -N --header "x-xana-auth: $xauthkey" $server/file/scrape-download-1.0-SNAPSHOT.jar

/usr/bin/java -cp dependencies.jar:scrape-download-1.0-SNAPSHOT.jar -DlogbackType=client sh.xana.forum.client.ClientMain
