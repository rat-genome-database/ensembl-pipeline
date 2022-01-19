#!/usr/bin/env bash
# shell script to run Ensembl pipeline
. /etc/profile

APPNAME=Ensembl
APPDIR=/home/rgddata/pipelines/$APPNAME

cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db2.xml \
  -Dlog4j.configurationFile=file://$APPDIR/properties/log4j2.xml \
  -jar lib/${APPNAME}.jar "$@" > $APPDIR/run.log
