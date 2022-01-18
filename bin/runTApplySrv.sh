# ----------------------------------------------------------------------------
# --
# -- Copyright (c) 2018 - EdsLab.  All Rights Reserved.
# --
# ----------------------------------------------------------------------------

current=`pwd`
NODEMST=""
NODESLV=""


PASSED_ARGUMENTS=$@

while [[ $# -gt 0 ]]
do
  i="$1"
    case $i in
      -n|--node)
       NODEMST="$2"
       shift # past argument
       shift # past value
      ;;
     -s|--slave)
       NODESLV="$2"
       shift # past argument
       shift # past value
      ;;
      *)    # unknown option
       shift # do nothing
      ;;
    esac
done

if [ -z "$RDBBDR" ]; then
    echo "RDBBDR must be set"
    exit -1
fi


if [ -z "$NODEMST" ]; then
    echo "NODEMST not specify, use runTApplySrv.sh -n nodemst -s nodeslv"
    exit 0
fi

if [ -z "$NODESLV" ]; then
    echo "NODESLV not specify, use runTApplySrv.sh -n nodemst -s nodeslv"
    exit 0
fi


echo "export NODEMST=$NODEMST" >  $RDBBDR/.current_master
echo "export NODESLV=$NODESLV" >  $RDBBDR/.current_slave
. $RDBBDR/.current_master
. $RDBBDR/.current_slave

cd $RDBBDR/bin

export LD_LIBRARY_PATH=$RDBBDR/lib:$LD_LIBRARY_PATH

ddd=$(date '+%Y-%m-%d-%H:%M:%S')

# logfile=$(echo "TApply_${node}_${nodes}_${ddd}.log")
logfile=$(echo "TApply_${NODEMST}_${NODESLV}_${ddd}.log")
echo "Logging startup messages to :" $logfile

echo "Launching.."

. $RDBBDR/etc/TCaptureRepSrv.config; . $RDBBDR/etc/runJavaApplication.sh; cd $RDBBDR/src ; runJREApplication -XX:-UsePerfData $JAVA_HEAP_SIZE -XX:ErrorFile=$RDBBDR/log/repserver_pid_%p.log -Djava.library.path=$RDBBDR/bin -Duser.timezone=UTC  -Djava.awt.headless=true  -cp $RDBBDR/lib/postgresql-42.2.19.jar:.   com.edslab.TApply $PASSED_ARGUMENTS  >> $logfile 2>&1 &
sleep 1
ps -aef --forest|grep -v grep |grep TApply|grep $PASSED_ARGUMENTS

cat $logfile
echo "press a to tail the log :"
read a

cd $current

echo $(cat ../conf/${NODESLV}_bdr_log.conf|grep "^java.util.logging.FileHandler.pattern"|awk -F '=' '{print $2}' )
echo " "
tail -999f $(cat ../conf/${NODESLV}_bdr_log.conf|grep "^java.util.logging.FileHandler.pattern"|awk -F '=' '{print $2}')
 
