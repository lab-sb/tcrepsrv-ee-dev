#!/bin/bash

nodeslv=$1
nodemst=$2
xid=$3
MyPID=$$
FaPID=$PPID
SRV=`hostname`


print_usage() {

echo ""
echo "Usage: tc_skip_xid_appy.sh currentnode slavenode masternode "
echo "Usage: tc_skip_xid_apply.sh [-V]"
echo ""

}

if [ -z "$nodeslv" ] ; then
  echo "NODEMST not set ! use tc_skip_xid_apply.sh nodeslv nodemst ( will skip current transaction from nodemst being applyed) "
  print_usage
  exit $STATE_UNKNOWN
fi


if [ -z "$nodemst" ] ; then
  echo "NODEMST not set ! use tc_skip_xid_apply.sh  nodeslv nodemst ( will skip current transaction from nodemst being applyed) "
  print_usage
  exit $STATE_UNKNOWN
fi



CHECKRUN=`ps -ef|grep skip_xid_runwal| grep -v ${MyPID}| grep -v ${FaPID} |grep -v  grep|wc -l`

if [ ${CHECKRUN} -gt 0 ]
    then
        #sleep ${nsleep}
        echo "Still in execution skip_xid_runwal .. Exit!"
        exit 0
fi

curxid=$(PGPASSWORD=rdbbdr_pwd psql -qtX -h localhost -U rdbbdr_user  rdb_db__${nodeslv} -c "select xid_offset from _rdb_bdr.walq__${nodemst}_offset")

echo "Current xid offset on db:rdb_db__${nodeslv} table:_rdb_bdr.walq__${nodemst}_offset is ${curxid}"
read A

if [ -z "$xid" ] ; then
	
        echo $(date '+%Y-%m-%d-%H:%M:%S:%N') " - Setting xid offset to  ${curxid} + 1 "
        PGPASSWORD=rdbbdr_pwd psql -qtX -h localhost -U rdbbdr_user  rdb_db__${nodeslv} -c "update _rdb_bdr.walq__${nodemst}_offset  set xid_offset = (select xid_offset +1 from _rdb_bdr.walq__${nodemst}_offset)"
        PGPASSWORD=rdbbdr_pwd psql -qtX -h localhost -U rdbbdr_user  rdb_db__${nodeslv} -c "select xid_offset from _rdb_bdr.walq__${nodemst}_offset"

else 
	if [ $xid -gt $curxid ] ; then
        	echo $(date '+%Y-%m-%d-%H:%M:%S:%N') " - Setting  xid offset to  ${xid}"
        	PGPASSWORD=rdbbdr_pwd psql -qtX -h localhost -U rdbbdr_user  rdb_db__${nodeslv} -c "update _rdb_bdr.walq__${nodemst}_offset  set xid_offset = ${xid}"
        	PGPASSWORD=rdbbdr_pwd psql -qtX -h localhost -U rdbbdr_user  rdb_db__${nodeslv} -c "select xid_offset from _rdb_bdr.walq__${nodemst}_offset"
	else
		echo " Xid parameter is <= then current xid, exit !"	
		exit -1
	fi	
fi



PGPASSWORD=rdbbdr_pwd psql -qtX -h localhost -U rdbbdr_user  rdb_db__${nodeslv} -c " select * from _rdb_bdr.walq__${nodemst} where xid>=(select xid_offset from _rdb_bdr.walq__${nodemst}_offset)";

