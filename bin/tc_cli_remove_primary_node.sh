#!/bin/sh
#
# ---------------------------------------------------------------------
# TC CLI remove primary node.
# Note: drop schema _rdb_bdr remove also table of the node is acting as slave 
# should be fixed moving the slave schema to _bdr_rdb instead of _rdb_bdr
# ---------------------------------------------------------------------
#

# Functions

#
# variableInFile()
#

variableInFile() {
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
echo "Usage: tc_cli_remove_primary_node.sh [--help] [-n node]"
echo "Usage: tc_cli_remove_primary_node.sh [-V]"
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

message "----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------"


# Check parameters

node=${2}

if [ -z "$RDBBDR" ] ; then
  echo "RDBBDR HOME not set !  Should be set in .rdbbdr_env.sh"
  print_usage
  exit $STATE_UNKNOWN
fi

title "-------------------------------------------------------- REMOVE PRIMARY NODE  ${node} ------------------------------------------------------------------------------------------------"
if [ -z "$node" ]; then
  message " Cannot start script"
  message "Required parameters are missing."
  message " > paramters  "
  message " node:${node}"
  message " "
  print_usage
  exit 1
fi

nconf=${node}_rdb_bdr.conf

host=$(variableInFile host ${RDBBDR}/conf/${nconf})
port=$(variableInFile port ${RDBBDR}/conf/${nconf})
user=$(variableInFile user ${RDBBDR}/conf/${nconf})
pwd=$(variableInFile pwd ${RDBBDR}/conf/${nconf})
db=$(variableInFile db ${RDBBDR}/conf/${nconf})
rdb=$(variableInFile rdb ${RDBBDR}/conf/${nconf})


walq=walq__${node}



echo "Read environment file : "$RDBBDR/.rdbbdr_env.sh

if [ ! -f $RDBBDR/.rdbbdr_env.sh ]; then
  echo "File $RDBBDR/.rdbbdr_env.sh  not found!"
  echo " Very Bad ! "
  exit $STATE_UNKNOWN
fi


nodemst=$(variableInFile NODEMST_${node} $RDBBDR/.rdbbdr_env.sh)
echo "variableInFile NODEMST_${node} ${RDBBDR}/.rdbbdr_env.sh "$nodemst

if [ -z "$nodemst" ] 
then
	echo "NODEMST_${node} is unset"
	echo "export NODEMST_${node}=${node}" >> ${RDBBDR}/.rdbbdr_env.sh 
else
	echo "NODEMST_${node} remove  ${node} from env file"
	sed -i '/NODEMST_${node}/d'   ${RDBBDR}/.rdbbdr_env.sh
fi


read a



if [ -z "$RDBBDR" ] ; then
  echo "RDBBDR HOME not set !  Should be set in .rdbbdr_env.sh"
  print_usage
  exit $STATE_UNKNOWN
fi

if [ "$user" != "rdbbdr_user" ] ; then
  echo "User must be rdbbdr_user (superuser)"
  print_usage
  exit $STATE_UNKNOWN
fi


if [ -z "$host" ] ; then
  echo "Missing fundamental argument: host"
  print_usage
  exit $STATE_UNKNOWN
fi


echo "SELECT EXISTS(select 1 from pg_database where datname='${db}') ;" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} 
status=$?

if [ $status -ne 0 ] ; then
	 echo "Invalid credentials provided or datatabase"
	 echo " create user : create user rdbbdr_user  superuser inherit login password 'rdbbdr_pwd'; "
        exit $STATE_UNKNOWN
fi
vv=$(echo "SELECT EXISTS(select 1 from pg_database where datname='${db}') ;" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} )
echo $vv

if [ $vv == "f" ] ; then
	  echo "Invalid datatabase"
	 echo " create db : create database ${db} ;"
        exit $STATE_UNKNOWN
fi




date
subtitle "Run remove primary node - host:" ${host} " user:" ${user}" port:" ${port}" pwd:"  ${pwd}" db:" ${db} "node:" ${node} 


echo "select pg_drop_replication_slot('rdb_slot_bdr');" |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} 
echo "select pg_drop_replication_slot('rdb_${node}_bdr');" |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} 
echo "select pg_drop_replication_slot('rdb_${node}_bdr');" |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${rdb} 



subtitle  "Remova also schema _rdb_bdr? [Enter]/[CTRL-C]"
read a


echo "drop schema _rdb_bdr cascade" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
echo "drop schema _rdb_bdr cascade" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${rdb}
echo "drop publication ${node}_publ " | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
echo "drop publication ${node}_publ " | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${rdb}

