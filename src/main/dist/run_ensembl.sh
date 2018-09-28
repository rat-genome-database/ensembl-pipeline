# run ensembl pipeline for rat, mouse and human
APPDIR=/home/rgddata/pipelines/Ensembl

# set variable HOST to uppercase hostname (f.e. KIRWAN, REED)
HOST=`hostname -s | tr '[a-z]' '[A-Z]'`

EMAIL_LIST=mtutaj@mcw.edu
if [ "$HOST" == "REED" ]; then
  EMAIL_LIST="mtutaj@mcw.edu,rgd.pipelines@mcw.edu"
fi

$APPDIR/run.sh -species Rat
$APPDIR/run.sh -species Mouse
$APPDIR/run.sh -species Human

mailx -s "[$HOST] Ensembl pipeline" $EMAIL_LIST < $APPDIR/logs/status.log
