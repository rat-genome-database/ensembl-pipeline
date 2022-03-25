# run ensembl pipeline for rat, mouse and human
APPDIR=/home/rgddata/pipelines/Ensembl

# set variable HOST to uppercase hostname (f.e. TRAVIS, REED)
HOST=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAIL_LIST=mtutaj@mcw.edu
if [ "$HOST" == "REED" ]; then
  EMAIL_LIST="mtutaj@mcw.edu"
fi

$APPDIR/run.sh -species Rat
cat $APPDIR/logs/summary.log > $APPDIR/all.log
$APPDIR/run.sh -species Mouse
cat $APPDIR/logs/summary.log >> $APPDIR/all.log
$APPDIR/run.sh -species Human
cat $APPDIR/logs/summary.log >> $APPDIR/all.log

mailx -s "[$HOST] Ensembl pipeline" $EMAIL_LIST < $APPDIR/all.log
