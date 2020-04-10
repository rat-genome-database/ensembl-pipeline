#!/usr/bin/env bash
# shell script to run Ensembl pipeline
. /etc/profile

APPNAME=Ensembl
APPDIR=/home/rgddata/pipelines/$APPNAME

cd $APPDIR

java -Dspring.config=$APPDIR/../properties/default_db.xml \
  -Dlog4j.configuration=file://$APPDIR/properties/log4j.properties \
  -jar lib/${APPNAME}.jar "$@" > $APPDIR/run.log
