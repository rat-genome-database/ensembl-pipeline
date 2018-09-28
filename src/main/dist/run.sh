#!/usr/bin/env bash
# shell script to run Ensembl pipeline
. /etc/profile

APPNAME=Ensembl
APPDIR=/home/rgddata/pipelines/$APPNAME

cd $APPDIR
pwd
DB_OPTS="-Dspring.config=$APPDIR/../properties/default_db.xml"
LOG4J_OPTS="-Dlog4j.configuration=file://$APPDIR/properties/log4j.properties"
export ENSEMBL_OPTS="$DB_OPTS $LOG4J_OPTS"

bin/$APPNAME "$@"
