# ----------------------------------------------------------------------------
# --
# -- Copyright (c) 2018 - EdsLab.  All Rights Reserved.
# --
# ----------------------------------------------------------------------------
NODEMST=""
current=`pwd`

PASSED_ARGUMENTS=$@

while [[ $# -gt 0 ]]
do
  i="$1"
    case $i in
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


if [ -z "$NODESLV" ]; then
#    NODESLV=`cat $RDBBDR/.rdbbdr_env| grep "NODESLV" | cut -f2 -d"="`
    echo "NODESLV not specify, use runTApplySrv.sh -s nodeslv"
    exit 0
fi

ps -aef --forest |grep TApply|grep $PASSED_ARGUMENTS
ps -aef --forest |grep TApply|grep $PASSED_ARGUMENTS |grep -v grep |awk '{print "kill -9 " $2}'|sh
sleep 2
ps -aef --forest |grep TApply|grep $PASSED_ARGUMENTS



#ps -ef|grep java|grep com.edslab.TApply|grep $PASSED_ARGUMENTS
#ps -ef|grep java|grep com.edslab.TApply|grep $PASSED_ARGUMENTS|grep -v grep |awk '{print "kill -9 " $2}'|sh
#sleep 2
#ps -ef|grep java|grep com.edslab.TApply|grep $PASSED_ARGUMENTS
