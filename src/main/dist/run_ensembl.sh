# run ensembl pipeline for rat, mouse and human
APPDIR=/home/rgddata/pipelines/ensembl-pipeline

# set variable HOST to uppercase hostname (f.e. TRAVIS, REED)
HOST=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAIL_LIST=mtutaj@mcw.edu
if [ "$HOST" == "REED" ]; then
  EMAIL_LIST="mtutaj@mcw.edu"
fi

$APPDIR/_run.sh -species Rat
cat $APPDIR/logs/summary.log > $APPDIR/all.log
$APPDIR/_run.sh -species Mouse
cat $APPDIR/logs/summary.log >> $APPDIR/all.log
$APPDIR/_run.sh -species Human
cat $APPDIR/logs/summary.log >> $APPDIR/all.log

mailx -s "[$HOST] Ensembl pipeline" $EMAIL_LIST < $APPDIR/all.log
