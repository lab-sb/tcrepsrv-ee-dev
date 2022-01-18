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
      -n|--node)
       NODEMST="$2"
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
#    NODEMST=`cat $RDBBDR/.rdbbdr_env| grep "NODEMST" | cut -f2 -d"="`
    echo "NODEMST not specify, use runTCaptureRepSrv.sh -n nodemst"
    exit 0
fi

echo "Listing running TCapture program for ${NODEMST}.." 
ps -aef --forest|grep -v grep|grep -v  statusTCaptureRepSrv |grep TCapture|grep $PASSED_ARGUMENTS
sleep 2
echo "Listing running TCapture program for all.." 
ps -aef --forest|grep -v grep|grep -v  statusTCaptureRepSrv |grep TCapture
