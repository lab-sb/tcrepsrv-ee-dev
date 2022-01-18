#!/bin/sh
#
# ---------------------------------------------------------------------
# TC CLI validator.
# ---------------------------------------------------------------------
#

# Functions

#
# variableInFile()
#

variableInFile () {
    variable=${1}
    file=${2}

    #source ${file}
    . ${file}
    eval value=\$\{${variable}\}
    echo ${value}
}


#
# print_usage()
#

print_usage() {

echo ""
echo "Usage: tc_cli_validator.sh [--help] [-n node] "
echo "Usage: tc_cli_validator.sh [-V]"
echo ""

}

#
# message()
#
message()
{
        #echo $*
         echo -e  ${Yellow}--$Color_Off - ${Green}${*}$Color_Off 2>&1
}

message_date()
{
        #echo $*
	 echo -e  ${Yellow}$(date)$Color_Off - ${Green}${*}$Color_Off 2>&1
}

title()
{
        #echo $*
         echo -e  ${Red}${*}$Color_Off 2>&1
}

subtitle()
{
        #echo $*
         echo -e  ${Purple}${*}$Color_Off 2>&1
}


#
# Environment
#
MyPID=$$
SRV=`hostname`
isok=0

# Reset
Color_Off='\e[0m'       # Text Reset

# Regular Colors
Black='\e[0;30m'        # Nero
Red='\e[0;31m'          # Rosso
Green='\e[0;32m'        # Verde
Yellow='\e[0;33m'       # Giallo
Blue='\e[0;34m'         # Blu
Purple='\e[0;35m'       # Viola
Cyan='\e[0;36m'         # Ciano
White='\e[0;37m'        # Bianco





#Check parameters

node=${2}
title "-------------------------------------------------------- VALIDATING PRIMARY NODE  ${node} ------------------------------------------------------------------------------------------------" 
if [ -z "$node" ]; then
  message " Cannot start script"
  message "Required parameters are missing."
  message " > paramters  "
  message " node:${node}"
  message " "
  print_usage
  exit 1
fi


# Check tools


UNAME=`which uname`
GREP=`which egrep`
GREP_OPTIONS=""
CUT=`which cut`
READLINK=`which readlink`
XARGS=`which xargs`
DIRNAME=`which dirname`
MKTEMP=`which mktemp`
RM=`which rm`
CAT=`which cat`
SED=`which sed`
MAIL=`which mailx`

if [ -z "$UNAME" -o -z "$GREP" -o -z "$CUT" -o -z "$MKTEMP" -o -z "$RM" -o -z "$CAT" -o -z "$SED"  -o -z "$MAIL" ]; then
  message "Required tools are missing - check beginning of \"$0\" file for details."
  exit 1
fi


if [ -z "$RDBBDR" ] ; then
  message "RDBBDR HOME not set !  Should be set in .rdbbdr_env.sh"
  print_usage
  exit $STATE_UNKNOWN
fi

message "Read environment file : "$RDBBDR/.rdbbdr_env.sh

if [ ! -f $RDBBDR/.rdbbdr_env.sh ]; then
  message "File $RDBBDR/.rdbbdr_env.sh  not found!"
  message " Very Bad ! set RDBBDR variable to HOME of rdbbdr software"
  exit $STATE_UNKNOWN
fi

nodemst=$(variableInFile NODEMST_${node} $RDBBDR/.rdbbdr_env.sh)

if [ -z "$nodemst" ]
then
         message "NODEMST_${node} is unset, fix it and retry validate!"
	exit -1
	
else
        message "NODEMST_${node} exits in $RDBBDR/.rdbbdr_env.sh"
fi

pconf=${node}_rdb_bdr.conf

if [ ! -f $RDBBDR/conf/${pconf} ]; then
  echo "File $RDBBDR/conf/${pconf}  not found,  fix it and retry validate!"
  exit $STATE_UNKNOWN
fi

echo 

host=$(variableInFile host $RDBBDR/conf/${pconf})
port=$(variableInFile port $RDBBDR/conf/${pconf})
user=$(variableInFile user $RDBBDR/conf/${pconf})
pwd=$(variableInFile pwd $RDBBDR/conf/${pconf})
db=$(variableInFile db $RDBBDR/conf/${pconf})

rhost=$(variableInFile rhost $RDBBDR/conf/${pconf})
rport=$(variableInFile rport $RDBBDR/conf/${pconf})
ruser=$(variableInFile ruser $RDBBDR/conf/${pconf})
rpwd=$(variableInFile rpwd $RDBBDR/conf/${pconf})
rdb=$(variableInFile rdb $RDBBDR/conf/${pconf})

#walqtrunc=10000000
#loglevel=FINE
#batch_size=10000
#DEBUG=${9:-no}
filter=$(variableInFile filter $RDBBDR/conf/${pconf})



walq=walq__${node}

subtitle "File conf:${pconf}"
message "host:${host} user:${user} port:${port} pwd:${pwd} db:${db} node:${node}  on RDB rhost:${rhost} ruser: ${ruser} rport:${rport} rpwd:${rpwd} rdb:${rdb}"
#  message " paramters  "
#  message " host:${host}"
#  message " user:${user}"
#  message " port:${port}"
#  message " pwd:${pwd}"
#  message " db:${db}"
#  message " node:${node}"
#  message " on RDB "
#  message " rhost:${rhost}"
#  message " ruser: ${ruser}"
#  message " rport:${rport}"
#  message " rpwd:${rpwd}"
##  message " rdb db name:${rdb}"
  message "Q table :${walq}"
message " "


#echo "select now()"|PGPASSWORD=${rpwd} psql -h ${rhost} -p ${rport} -U ${ruser} ${rdb}
subtitle "Check slot"
isSlotActive=$(echo "select active from pg_replication_slots where slot_name ='rdb_${node}_bdr';"|PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}  )
if [ $(echo $isSlotActive | tr -d ' ') = "t" ] ; then
message "       Slot 'rdb_${node}_bdr' is active "
else
message "       Slot 'rdb_${node}_bdr' NOT active  "
 exit $STATE_UNKNOWN
fi


echo "SELECT 'slot '||slot_name , 'behind_in_GB '||(pg_current_wal_flush_lsn() - confirmed_flush_lsn) / 1024 /1024/1024
FROM pg_replication_slots 
WHERE (( pg_current_wal_flush_lsn() - confirmed_flush_lsn ) / 1024 /1024/1024> 0 
OR confirmed_flush_lsn IS NULL ) and slot_name ='rdb_${node}_bdr';"|PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}  




#publstr=$(echo "SELECT n.nspname, c.relname
#        FROM pg_catalog.pg_class c,
#             pg_catalog.pg_namespace n,
#             pg_catalog.pg_publication_rel pr
#        WHERE c.relnamespace = n.oid
#          AND c.oid = pr.prrelid
#          AND pr.prpubid = (SELECT oid FROM pg_catalog.pg_publication)
#        ORDER BY 1,2" |PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb} )
#echo "publication "$publstr

#publstr=$(echo "SELECT n.nspname, c.relname, p.pubname FROM pg_catalog.pg_class c, pg_catalog.pg_namespace n,  pg_catalog.pg_publication_rel pr, pg_catalog.pg_publication p
publstr=$(echo "SELECT 1 FROM pg_catalog.pg_class c, pg_catalog.pg_namespace n,  pg_catalog.pg_publication_rel pr, pg_catalog.pg_publication p
        WHERE c.relnamespace = n.oid AND c.oid = pr.prrelid  AND pr.prpubid = p.oid and p.pubname = '${node}_publ' and c.relname = '${walq}' " |PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}  )

#echo "publication:"$publstr | tr -d ' '
#message "Publication  '${node}_publ' exits on '${walq}' ? : "$publstr

#message " check  "
if [ $(echo $publstr | tr -d ' ') = "1" ] ; then
message "       Publication  '${node}_publ' exits on table '_rdb_bdr.${walq}' "
else
message "       Publication  '${node}_publ' NOT exits on table '_rdb_bdr.${walq}' "
 exit $STATE_UNKNOWN
fi


subtitle "Check filters set  "
if [ -z "$filter" ]
then
         message "filter is unset, all db xacts captured!"
else
        message "filter is set as follow: "
	echo "select * from _rdb_bdr.${walq}_filtro "  |PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}
fi


subtitle " 	 lastt 5 rows in ${walq}_xid "
echo " select * from _rdb_bdr.${walq}_xid where xid_current >= (select max(xid_current) -5  from _rdb_bdr.${walq}_xid) order by dateop desc;"| PGPASSWORD=${pwd} psql -X -h ${host} -p ${port} -U ${user} ${db}


subtitle "       last 5 rows in ${walq} "
echo "select * from _rdb_bdr.${walq} where xid = (select max(xid) from _rdb_bdr.${walq}) order by wid desc limit 5;" | PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}  

subtitle "       gaps between DB and Q "

echo "INSERT INTO _rdb_bdr.${walq}_mon  (db_xid_last_committed, db_last_committed_dateop, wal_lsn, q_xid, q_dateop,q_lsn, state)
 select  cast(cast(xxx.xid AS text) AS int)  ,xxx.timestamp,pg_current_wal_lsn,qqq.xid, qqq.dateop,qqq.lsn , active   from pg_last_committed_xact() xxx,pg_current_wal_lsn(), _rdb_bdr.${walq} qqq ,pg_replication_slots
 where  slot_name='rdb_${node}_bdr' and  qqq.xid = (select max(xid) from _rdb_bdr.${walq} ) limit 1 " | PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}


echo "select 'gap_lsn_bytes:'|| wal_lsn - q_lsn as gap_lsn_bytes , 'gap_xid:'||db_xid_last_committed - q_xid as gap_xid , 'gap_ms:'||date_part('millisecond',db_last_committed_dateop - q_dateop) as gap_ms , *  from _rdb_bdr.${walq}_mon order by check_dateop desc limit 1" | PGPASSWORD=${rpwd} psql -X -h ${rhost} -p ${rport} -U ${ruser} ${rdb}


subtitle "       gaps between DB and Q "
#echo "select  'Q_max_xid  :'||xid as Q_max_xid,'DB_txid_current :'|| txid_current() ,'gap DB vs Q:'||  txid_current()-xid  as gap_DB_vs_Q ,'Q_xid_committing :'|| current_xid as Q_xid_committing,'DB_redo_lsn :'||redo_lsn ,'Q_lsn :'||lsn , 'GB_behind :'||(redo_lsn-lsn) / 1024 / 1024 / 1024  AS GB_behind from  pg_control_checkpoint(),_rdb_bdr.${walq} where xid = (select max(xid) from _rdb_bdr.${walq});" | PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}

echo "select  'Q_max_xid  :'||q.xid as Q_max_xid,'DB_txid_current :'|| x.xid as DB_txid_current ,'gap DB vs Q:'||  cast(cast(x.xid AS text) AS int) -q.xid  as gap_DB_vs_Q ,
'Q_xid_committing :'|| current_xid as Q_xid_committing,'DB_redo_lsn :'||redo_lsn  as DB_redo_lsn ,'Q_lsn :'||lsn as Q_lsn, 
'GB_behind :'||(redo_lsn-lsn) / 1024 / 1024 / 1024  AS GB_behind , 'DB_timestamp :' || x.timestamp as DB_timestamp , 'current_timestamp :' ||current_timestamp as current_timestamp
from  pg_last_committed_xact() x, pg_control_checkpoint() c ,_rdb_bdr.${walq} q
where q.xid = (select max(xid) from _rdb_bdr.${walq} ) limit 1; " | PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}




#message " check  "
subtitle "       TCapture is running .. "
ps -aef --forest|grep -v grep|grep -v  statusTCaptureRepSrv |grep TCapture|grep -n ${node}

#message " check  "
message "   "
subtitle "       TCapture Log .. "
tail -9 $(cat ../conf/${node}_log.conf|grep "^java.util.logging.FileHandler.pattern"|awk -F '=' '{print $2}') 


slaves=$(echo "select slot_name from pg_replication_slots where slot_name like '${node}%';"|PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}  )

for i in $slaves
do
	slave=$(echo $i|awk -F '_' '{print $3}')
title "-------------------------------------------------------- VALIDATING  SLAVE NODE ${slave} ------------------------------------------------------------------------------------------------"
	subtitle "slave # "$slave

	isSlotActive=$(echo "select active from pg_replication_slots where slot_name ='$i';"|PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}  )
	if [ $(echo $isSlotActive | tr -d ' ') = "t" ] ; then
		message "       Slot '${i}' is active "
	else
		message "       Slot '${i}' NOT active  "
	fi

subtitle "publication slot on ${node} for slave ${slave}"     
echo "SELECT 'slot '||slot_name , 'behind_in_GB '||(pg_current_wal_flush_lsn() - confirmed_flush_lsn) / 1024 /1024/1024
FROM pg_replication_slots where slot_name ='${i}';"|PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}

#WHERE (( pg_current_wal_flush_lsn() - confirmed_flush_lsn ) / 1024 /1024/1024> 0
#OR confirmed_flush_lsn IS NULL ) and slot_name ='${i}';"|PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}




sconf=${slave}_bdr_rdb.conf

if [ ! -f $RDBBDR/conf/${sconf} ]; then
  echo "File $RDBBDR/conf/${sconf}  not found,  fix it and retry validate!"
  exit $STATE_UNKNOWN
fi

echo

shost=$(variableInFile host $RDBBDR/conf/${sconf})
sport=$(variableInFile port $RDBBDR/conf/${sconf})
suser=$(variableInFile user $RDBBDR/conf/${sconf})
spwd=$(variableInFile pwd $RDBBDR/conf/${sconf})
sdb=$(variableInFile db $RDBBDR/conf/${sconf})

srhost=$(variableInFile rhost $RDBBDR/conf/${sconf})
srport=$(variableInFile rport $RDBBDR/conf/${sconf})
sruser=$(variableInFile ruser $RDBBDR/conf/${sconf})
srpwd=$(variableInFile rpwd $RDBBDR/conf/${sconf})
srdb=$(variableInFile rdb $RDBBDR/conf/${sconf})

#walqtrunc=10000000
#loglevel=FINE
#batch_size=10000

snode=${slave}
walqs=walq__${slave}

subtitle "File conf:${sconf}"
message "host:${shost} user:${suser} port:${sport} pwd:${spwd} db:${sdb} node:${snode}  on RDB rhost:${srhost} ruser: ${sruser} rport:${srport} rpwd:${srpwd} rdb:${srdb}"


subExists=$(	echo "select exists (select 1 from pg_subscription where subname = '${snode}_subs_${node}');" |PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb})  
if [ $(echo $subExists| tr -d ' ') = "t" ] ; then
                message "       Subscription '${snode}_subs_${node}' exists on ${snode} "
        else
                message "       Subscription '${snode}_subs_${node}' NOT exits on ${snode} "
		continue
        fi




summarizeSub=$( echo "select 'subenabled:'||subenabled, 'subconninfo:'||subconninfo , 'subslotname:'||subslotname  from pg_subscription  where subname = '${snode}_subs_${node}' ;" |PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb})
    message "       Subscription '${snode}_subs_${node}' summarize ${summarizeSub} "

    subtitle "       last 5 rows in ${walqs} "
echo "select * from _rdb_bdr.${walq} where wid >= (select max(wid)-3 from _rdb_bdr.${walq}) order by dateop desc limit 3;" | PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb}


   subtitle "       gaps between  Q on primary .."

echo "select  'Q_max_xid  :'||q.xid as Q_max_xid , 'Q dateop :'||q.dateop from _rdb_bdr.${walq} q
where q.xid = (select max(xid) from _rdb_bdr.${walq} ) limit 1; " | PGPASSWORD=${rpwd} psql -qtX -h ${rhost} -p ${rport} -U ${ruser} ${rdb}

   subtitle "       and  Q on slave"

echo "select  'Q_max_xid  :'||q.xid as Q_max_xid , 'Q dateop :'||q.dateop from _rdb_bdr.${walq} q
where q.xid = (select max(xid) from _rdb_bdr.${walq} ) limit 1; "| PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb}

   subtitle "       gaps between Apply DB and Q "

   echo "select 'gap Q vs DB target ' || xid - xid_offset  from _rdb_bdr.${walq}_offset , _rdb_bdr.${walq} where xid= (select max(xid) from _rdb_bdr.${walq}) limit 1;" | PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb}

   echo "select count(*) from (select distinct xid  from _rdb_bdr.${walq} where xid > (select xid_offset from _rdb_bdr.${walq}_offset )  and xid <= (select max(xid) from _rdb_bdr.${walq} )) as foo ;"  | PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb}


  subtitle "Check TApply process is running.."
 #echo " select exists (select  1 from  pg_stat_activity where datname = '${srdb}'  and query_start > now() - interval '3 seconds' and substring(query,2,10) = 'select xid')" | PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb}
 echo " select exists (select  1 from  pg_stat_activity where datname = '${srdb}'  and query_start > now() - interval '3 seconds' and query not like '%pg_stat_activity%' and query like '%${node}%')" | PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb}
	
  # no is local ps
  # echo " \! ps -aef --forest|grep -v grep|grep -v  statusTApply |grep TApply |grep -n ${snode} " | PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb}

subtitle "Check filters set"
echo " select * from _rdb_bdr.${walq}_filtro " | PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb}
subtitle "Check conflicts table"
echo " select * from _rdb_bdr.${walq}_conflicts order by dateop desc limit 1" | PGPASSWORD=${srpwd} psql -qtX -h ${srhost} -p ${srport} -U ${sruser} ${srdb}
 #RC=$?
        #if [ "$RC" != 0 ]
        #then
		#echo "Error"
	#fi

	

done

