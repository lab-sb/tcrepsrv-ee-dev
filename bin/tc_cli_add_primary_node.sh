#!/bin/sh
#
# ---------------------------------------------------------------------
# TC CLI add primary node script.
# ---------------------------------------------------------------------
#

# Functions

#
# variableInFile()
#

variableInFile () {
    variable=${1}
    file=${2}

    . ${file}
    eval value=\$\{${variable}\}
    echo ${value}
}


#
# print_usage()
#

print_usage() {

echo ""
echo "Usage:   sh tc_cli_add_primary_node.sh [--help] [-h host] [-u user] [-p port] [-pwd pwd] [-db database] [-n node] [-rh rhost] [-ru ruser] [-rp rport] [-rpwd pwd] "
echo "Example: sh tc_cli_add_primary_node.sh -h localhost -u rdbbdr_user -p 5433 -pwd rdbbdr_pwd -db nyc_db -n nyci -rh localhost -ru rdbbdr_user -rp 5433  -rpwd rdbbdr_pwd "
echo "Usage: tc_cli_add_primary_node.sh [-V]"
echo ""
}

#
# message()
#

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


title "------------------------------------------------------------------ ADD PRIMARY NODE  -------------------------------------------------------------------------------------------------------------------------"


while test -n "$1"; do

  case "$1" in

    --help)
        print_usage
        exit $STATE_OK
        ;;

    -V)
        print_usage
        exit $STATE_OK
        ;;

    -h)
        host=$2
        shift
        ;;

    -u)
        user=$2
        shift
        ;;

    -p)
        port=$2
        shift
        ;;

    -pwd)
        pwd=$2
        shift
        ;;

    -db)
        db=$2
        shift
        ;;

    -n)
        node=$2
        shift
        ;;

    -rh)
        rhost=$2
        shift
        ;;

    -ru)
        ruser=$2
        shift
        ;;

    -rp)
        rport=$2
        shift
        ;;

    -rp)
        rport=$2
        shift
        ;;
    -rpwd)
        rpwd=$2
        shift
        ;;

    *)
        echo "Unknown argument: $1"
        print_usage
        exit $STATE_UNKNOWN
        ;;
  esac
  shift

done

# Check parameters
#host=${2}
#user=${4}
#port=${6}
#pwd=${8}
#db=${10}
#node=${12}
#
#rhost=${14}
#ruser=${16}
#rport=${18}
#rpwd=${20}
#vrdb=${22}

vrdb=rdb_db
rdb=${vrdb}__${node}
walq=walq__${node}
nconf=${node}_rdb_bdr.conf

#walqtrunc=10000000
#loglevel=FINE
#batch_size=10000



if [ -z "$host" -o -z "$user" -o -z "$port" -o -z "$pwd" -o -z "$db" -o -z "$node" -o -z "$rhost" -o -z "$ruser" -o -z "$rport" -o -z "$rpwd" -o -z "$vrdb" ]; then
  message " Cannot start script"
  message "Required parameters are missing."
  message " > paramters  "
  message " host:${host}"
  message " user:${user}"
  message " port:${port}"
  message " pwd:${pwd}"
  message " db:${db}"
  message " node:${node}"
  message " > on RDB "
  message " rhost:${rhost}"
  message " ruser: ${ruser}"
  message " rport:${rport}"
  message " rpwd:${rpwd}"
  message " rdb:${vrdb}"
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


if [ -z "$UNAME" -o -z "$GREP" -o -z "$CUT" -o -z "$MKTEMP" -o -z "$RM" -o -z "$CAT" -o -z "$SED" ]; then
  message "Required tools are missing - check beginning of \"$0\" file for details."
  exit 1
fi


if [ -z "$RDBBDR" ] ; then
  message "RDBBDR HOME not set !  Should be set in .rdbbdr_env.sh"
  print_usage
  exit $STATE_UNKNOWN
fi

if [ "$user" != "rdbbdr_user" ] ; then
  message "User must be rdbbdr_user (superuser)"
  print_usage
  exit $STATE_UNKNOWN
fi


if [ -z "$host" ] ; then
  message "Missing fundamental argument: host"
  print_usage
  exit $STATE_UNKNOWN
fi


subtitle "Read environment file : "$RDBBDR/.rdbbdr_env.sh

if [ ! -f $RDBBDR/.rdbbdr_env.sh ]; then
  message "File $RDBBDR/.rdbbdr_env.sh  not found!"
  message " Very Bad ! set RDBBDR variable to HOME of rdbbdr software"
  exit $STATE_UNKNOWN
fi


nodemst=$(variableInFile NODEMST_${node} $RDBBDR/.rdbbdr_env.sh)

if [ -z "$nodemst" ]
then
        # message "NODEMST_${node} is unset"
        echo "export NODEMST_${node}=${node}" >> ${RDBBDR}/.rdbbdr_env.sh
else
        #message "NODEMST_${node} substitute with ${node}"
        sed -i 's/NODEMST_${node}=${nodemst}/NODEMST_${node}=${node}/g'  ${RDBBDR}/.rdbbdr_env.sh
fi

OS_TYPE=`"$UNAME" -s`
OS_NAME=$(cat /etc/redhat-release |awk '{ print $1}')

if  [ "$OS_TYPE" == "Linux" -o "$OS_NAME" == "CentOS" -o "$OS_NAME" == "Ubuntu" ]
then
if [ ! -f /usr/pgsql-10/lib/rdblogdec.so ]; then # -o ! -f /usr/lib/postgresql/10/lib/rdblogdec.so ]; then
  message "File rdblogdec.so not found!"
  exit $STATE_UNKNOWN
fi
fi

date
subtitle "Run add primary node - host:${host} user:${user} port:${port} pwd:${pwd} db:${db} node:${node}  on RDB rhost:${rhost} ruser: ${ruser} rport:${rport} rpwd:${rpwd} rdb:${rdb}" 
  message " paramters  "
  message " host:${host}"
  message " user:${user}"
  message " port:${port}"
  message " pwd:${pwd}"
  message " db:${db}"
  message " node:${node}"
  message " on RDB "
  message " rhost:${rhost}"
  message " ruser: ${ruser}"
  message " rport:${rport}"
  message " rpwd:${rpwd}"
  message " rdb:${vrdb}"
  message " "
  message " rdb db name:${rdb}"
  message " table walq node:${walq}"
  message " node conf:${nconf}"
message " "


subtitle "Checking existance of database ${db}..."

echo "SELECT EXISTS(select 1 from pg_database where datname='${db}') ;" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} 
status=$?

if [ $status -ne 0 ] ; then
	 message "Invalid credentials provided or datatabase"
	 message "please, create user: 	create user rdbbdr_user  superuser inherit login password 'rdbbdr_pwd'; "
        exit $STATE_UNKNOWN
fi
vv=$(echo "SELECT EXISTS(select 1 from pg_database where datname='${db}') ;" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} )
message "Esiste database ${db} ? : "$vv

if [ "$vv" = " f" ] ; then
	 message "Invalid datatabase"
	 message "please, create db : create database ${db} ;"
        exit $STATE_UNKNOWN
fi


subtitle "Checking existance of database ${rdb}..."


echo "SELECT EXISTS(select 1 from pg_database where datname='${rdb}') ;" | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb} 
status=$?

if [ $status -ne 0 ] ; then
         message "Invalid credentials provided or datatabase"
         message "please, create user : create user rdbbdr_user  superuser inherit login password 'rdbbdr_pwd'; "
        exit $STATE_UNKNOWN
fi
vv=$(echo "SELECT EXISTS(select 1 from pg_database where datname='${rdb}') ;" | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb}  )
message "Esiste database ${rdb} ? : "$vv

if [ "$vv" = " f" ] ; then
         message "Invalid datatabase"
         message " create db : create database ${db} ;"
        exit $STATE_UNKNOWN
fi


echo "Creating ${nconf} file"
echo "host=${host}" 	>  $RDBBDR/conf/${nconf}
echo "user=${user}" 	>>  $RDBBDR/conf/${nconf}
echo "port=${port}" 	>>  $RDBBDR/conf/${nconf}
echo "pwd=${pwd}" 	>>  $RDBBDR/conf/${nconf}
echo "db=${db}" 	>>  $RDBBDR/conf/${nconf}
echo "node=${node}" 	>>  $RDBBDR/conf/${nconf}
echo "rhost=${rhost}" 	>>  $RDBBDR/conf/${nconf}
echo "ruser=${ruser}" 	>>  $RDBBDR/conf/${nconf}
echo "rport=${rport}" 	>>  $RDBBDR/conf/${nconf}
echo "rpwd=${rpwd}" 	>>  $RDBBDR/conf/${nconf}
echo "rdb=${rdb}" 	>>  $RDBBDR/conf/${nconf}
echo "walqtrunc=10000000"  >>  $RDBBDR/conf/${nconf}
echo "batch_size=1000"  >>  $RDBBDR/conf/${nconf}
echo "loglevel=FINE " 	>>  $RDBBDR/conf/${nconf}


message " Press a to continue"
read a


# PGPASSWORD=${pwd} dropdb -U ${user} -h ${host} -p ${port} ${db}
# PGPASSWORD=${pwd} createdb -U ${ruser} -h ${rhost} -p ${rport} ${rdb}
# echo "drop schema _rdb_bdr cascade" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
#val=$(echo "SELECT EXISTS(select 1 from information_schema.schemata where  schema_name = '_rdb_bdr'); " | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db})

val=$(echo "SELECT EXISTS(select 1 from information_schema.schemata where  schema_name = '_rdb_bdr'); " | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db})

status=$?

echo "Schema _rdb_bdr esiste in ${db} ? :" $val

if [ "$val" = " t" ]
then
	echo "schema _rdb_bdr already exists,  please verify !"	
	echo "also check existing  publication, subscriptions, replication slots"
	message " Press a to continue"
	read -p "Do you want to drop _rdb_bdr? [Y/n] " answ
fi

if [ "$answ" = "Y" ]
then
	echo "drop schema _rdb_bdr cascade" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
fi

subtitle " Creating primary node _rdb_bdr structure "

sed "s/walq__master/${walq}/g"   ${RDBBDR}/sql/primary_node_structure_stampo_master_rdbbdr.sql  > ${RDBBDR}/tmp/primary_node_structure_stampo_master_rdbbdr_${node}.sql
PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db} < ${RDBBDR}/tmp/primary_node_structure_stampo_master_rdbbdr_${node}.sql

echo "Finish Creating primary node _rdb_bdr structure , please check ..."

 message " Press a to continue"

val=$(echo "SELECT EXISTS(select 1 from information_schema.schemata where  schema_name = '_rdb_bdr'); " | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb})

status=$?

echo "Schema _rdb_bdr esiste in ${rdb} ? :" $val

if [ "$val" = " t" ]
then
        echo "schema _rdb_bdr already exists,  please verify !"
        echo "also check existing  publication, subscriptions, replication slots"
        message " Press a to continue"
        read -p "Do you want to drop _rdb_bdr? [Y/n] " answ
fi

if [ "$answ" = "Y" ]
then
        echo "drop schema _rdb_bdr cascade" | PGPASSWORD=${rpwd} psql -U ${ruser} -h ${rhost} -p ${rport} ${rdb}
fi


subtitle " Creating rdb  node _rdb_bdr structure "
message " Press a to continue"
read a


sed "s/walq__master/${walq}/g"   ${RDBBDR}/sql/primary_node_structure_stampo_rdb_rdbbdr.sql  > ${RDBBDR}/tmp/primary_node_structure_stampo_rdb_rdbbdr_${node}.sql

PGPASSWORD=${rpwd} psql  -U ${ruser} -h ${rhost} -p ${rport} ${rdb} < ${RDBBDR}/tmp/primary_node_structure_stampo_rdb_rdbbdr_${node}.sql

echo "Finish Creating rdb node _rdb_bdr structure , please check ..."
message " Press a to continue"
read a

subtitle "set search_path for rdbbdr_user in ${rdb} ......."

echo "ALTER ROLE rdbbdr_user IN DATABASE ${db}  SET search_path TO _rdb_bdr;"| PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
echo "ALTER ROLE rdbbdr_user IN DATABASE ${rdb}  SET search_path TO _rdb_bdr;" | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb}

subtitle "Drop/Creating Publication ${node}_publ for table _rdb_bdr.${walq} .. "

val=$(echo "SELECT EXISTS(select 1 from  pg_publication where pubname = '${node}_publ'); " | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb})

status=$?

echo "Publication ${node}_publ esiste  in ${rdb} ? :" $val

if [ "$val" = " t" ]
then
        echo "Publication ${node}_publ  already exists,  please verify !"
        message " Press a to continue and drop publication ${node}_publ .."
	 read -p "Do you want to drop ${node}_publ ? [Y/n] " answ
fi


if [ "$answ" = "Y" ]
then
	echo "drop publication ${node}_publ " | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb}
fi


echo "create publication ${node}_publ for table _rdb_bdr.${walq};"  | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb} 

val=$(echo "SELECT EXISTS(select 1 from  pg_publication where pubname = '${node}_publ'); " | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb})
if [ "$val" = " t" ]
then
        echo "Publication ${node}_publ created  !"
fi


subtitle "Dropping Creating slot ${node}_slot .. "

val=$(echo "SELECT EXISTS(select 1 from  pg_replication_slots where slot_name = 'rdb_${node}_bdr'); " | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb})

status=$?

echo "Check slot rdb_${node}_bdr esiste  in ${rdb} ? :" $val

if [ "$val" = " t" ]
then
        echo "slot rdb_${node}_bdr   already exists,  please verify !"
        message " Press a to continue and drop slot rdb_${node}_bdr  .. or ctrl-c to exit"
        read a
	echo " select  pg_drop_replication_slot('rdb_${node}_bdr');" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
fi

echo "Creating slot rdb_${node}_bdr"
echo " SELECT * FROM pg_create_logical_replication_slot('rdb_${node}_bdr', 'rdblogdec');"  | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}



#subtitle "copying conf/master_log.conf to conf/${node}_log.conf "
#cp $RDBBDR/conf/master_log.conf $RDBBDR/conf/${node}_log.conf
#sed "s/master/${node}/g"   ${RDBBDR}/conf/master_log.conf  > ${RDBBDR}/conf/${node}_log.conf



echo "insert into _rdb_bdr.tc_process (n_id,n_name,n_shouldbe,n_state,n_operation,n_type,n_master,n_dateop,n_datecrea,n_pid) values (1,'${node}', 'up','stop','managed','M','${node}', now(),now(),-1);" |PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb}
echo "insert into _rdb_bdr.tc_process (n_id,n_name,n_shouldbe,n_state,n_operation,n_type,n_master,n_dateop,n_datecrea,n_pid) values (1,'${node}', 'up','stop','managed','H','${node}', now(),now(),-1);" | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb}

