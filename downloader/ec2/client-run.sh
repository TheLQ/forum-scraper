#!/bin/bash
set -euo pipefail
set -x

aws s3 cp s3://xana.sh/config-client.properties .

xauthkey=$(grep server.auth config-client.properties | cut -d'=' -f2)

wget -N --header "x-xana-auth: $xauthkey" http://172.31.39.212:8080/file/dependencies.jar
wget -N --header "x-xana-auth: $xauthkey" http://172.31.39.212:8080/file/scrape-download-1.0-SNAPSHOT.jar

/usr/bin/java -cp dependencies.jar:scrape-download-1.0-SNAPSHOT.jar -DlogbackType=client sh.xana.forum.client.ClientMain
