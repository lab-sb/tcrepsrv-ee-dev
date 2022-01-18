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

echo "Listing running TApplSrv program for ${NODESLV}.."
ps -aef --forest|grep -v grep|grep -v  statusTApplSrv |grep TAppl|grep $PASSED_ARGUMENTS

sleep 2
echo "Listing running TApplSrv program for all.."
ps -aef --forest|grep -v grep|grep -v  statusTApplSrv |grep TAppl


