#!/bin/sh
#
# ---------------------------------------------------------------------
# TC CLI add remote node script.
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
echo "Usage: tc_cli_add_remote_node_conf.sh [--help] [-h host] [-u user] [-p port] [-pwd pwd] [-db database] [-n node] [-rh rhost] [-ru ruser] [-rp rport] [-rpwd pwd] [-rdb database (rdb_db)] "
echo "Usage: tc_cli_add_remote_node_conf.sh [-V]"
echo "Example:  tc_cli_add_remote_node_conf.sh  -h vlondb-glf01  -u rdbbdr_user -p 5432 -pwd rdbbdr_pwd -db lon_db -n lond  -rh vlondb-glf01 -ru rdbbdr_user -rport 5432  -rp rdbbdr_pwd -vrdb rdb_db"
echo ""
# da milano come slave su london
# sh add_remote_node_conf_with_rdb.sh -h vlondb-glf01  -u rdbbdr_user -p 5432 -pwd rdbbdr_pwd -db lon_db -n lond  -rh vlondb-glf01 -ru rdbbdr_user -rport 5432  -rp rdbbdr_pwd -vrdb rdb_db


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


title "------------------------------------------------------------------ ADD REMOTE NODE CONF -------------------------------------------------------------------------------------------------------------------------"

# Check parameters


host=${2}
user=${4}
port=${6}
pwd=${8}
db=${10}
node=${12}
rhost=${14}
ruser=${16}
rport=${18}
rpwd=${20}
vrdb=${22}

rdb=${vrdb}__${node}
walq=walq__${node}
nconf=${node}_rdb_bdr.conf


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




nodemst=$(variableInFile REMOTEMST_${node} $RDBBDR/.rdbbdr_env.sh)
echo "(variableInFile REMOTEMST_${node} ${RDBBDR}/.rdbbdr_env.sh "$nodemst

if [ -z "$nodemst" ]
then
        echo "REMOTEMST_${node} is unset"
        echo "export REMOTEMST_${node}=${node}" >> ${RDBBDR}/.rdbbdr_env.sh
else
        echo "REMOTEMST_${node} substitute with ${node}"
        sed -i 's/REMOTEMST_${node}=${nodemst}/REMOTEMST_${node}=${node}/g'  ${RDBBDR}/.rdbbdr_env.sh
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


echo "select 1 from pg_database where datname='${db}' ;" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}

status=$?

if [ $status -ne 0 ] ; then
	 echo "Invalid credentials provided or datatabase"
        exit $STATE_UNKNOWN
fi


subtitle "Creating ${nconf} .."
echo "host=${host}" >  $RDBBDR/conf/${nconf}
echo "user=${user}" >>  $RDBBDR/conf/${nconf}
echo "port=${port}" >>  $RDBBDR/conf/${nconf}
echo "pwd=${pwd}" >>  $RDBBDR/conf/${nconf}
echo "db=${db}" >>  $RDBBDR/conf/${nconf}
echo "node=${node}" >>  $RDBBDR/conf/${nconf}
echo "rhost=${rhost}" >>  $RDBBDR/conf/${nconf}
echo "ruser=${ruser}" >>  $RDBBDR/conf/${nconf}
echo "rport=${rport}" >>  $RDBBDR/conf/${nconf}
echo "rpwd=${rpwd}" >>  $RDBBDR/conf/${nconf}
echo "rdb=${rdb}" >>  $RDBBDR/conf/${nconf}
echo "walqtrunc=10000000"  >>  $RDBBDR/conf/${nconf}
echo "batch_size=1000"  >>  $RDBBDR/conf/${nconf}
echo "loglevel=FINE "   >>  $RDBBDR/conf/${nconf}



date
subtitle "Added remote node ${node} conf- host:" ${host} " user:" ${user}" port:" ${port}" pwd:"  ${pwd}" db:" ${db} "node:" ${node} 









