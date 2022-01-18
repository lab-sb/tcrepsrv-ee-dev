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
message "Usage: tc_cli_join_a_remote_node.sh [--help] [-h host] [-u user] [-p port] [-pwd pwd] [-rdb database] [-n node] [-p primary] "
echo "Example: sh tc_cli_join_a_remote_node.sh -h vmildb-glf01 -u rdbbdr_user -p 5432 -pwd rdbbdr_pwd -db rdb_db__flor -n flor -p lond "
echo "Usage: tc_cli_join_a_remote_node.sh [-V]"
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


title "------------------------------------------------------------------ JOIN A REMOTE PRIMARY NODE  -------------------------------------------------------------------------------------------------------------------------"

# Check parameters

host=${2}
user=${4}
port=${6}
pwd=${8}
db=${10}
node=${12}

primary=${14}
ismmr=${16}



nconf=${node}_bdr_rdb.conf
pconf=${primary}_rdb_bdr.conf
walqs=walq__${node}
walqp=walq__${primary}

if [ -z "$host" -o -z "$user" -o -z "$port" -o -z "$pwd" -o -z "$db" -o -z "$node" -o -z "$primary" ]; then
  message " Cannot start script"
  message "Required parameters are missing."
  message " > paramters on RDB "
  message " host:${host}"
  message " user:${user}"
  message " port:${port}"
  message " pwd:${pwd}"
  message " db:${db}"
  message " node:${node}"
  message " "
  message " primary node:${primary}"
  message " "
  print_usage
  exit 1
fi

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

if [ "$db" != "rdb_db__${node}" ] ; then
  message "Db name should be the RDB db : rdb_db__${node} and not $db"
  print_usage
  exit $STATE_UNKNOWN
fi


if [ -z "$host" ] ; then
  echo "Missing fundamental argument: host"
  print_usage
  exit $STATE_UNKNOWN
fi

message "Checking existance of database ${db}..."

echo "select 1 from pg_database where datname='${db}' ;" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}

status=$?

if [ $status -ne 0 ] ; then
	 echo "Invalid credentials provided or datatabase"
        exit $STATE_UNKNOWN
fi



echo "Creating ${nconf} file"
echo "host=${host}"     >  $RDBBDR/conf/${nconf}
echo "user=${user}"     >>  $RDBBDR/conf/${nconf}
echo "port=${port}"     >>  $RDBBDR/conf/${nconf}
echo "pwd=${pwd}"       >>  $RDBBDR/conf/${nconf}
echo "db=${db}"         >>  $RDBBDR/conf/${nconf}
echo "node=${node}"     >>  $RDBBDR/conf/${nconf}
echo "rhost=${rhost}"   >>  $RDBBDR/conf/${nconf}
echo "ruser=${ruser}"   >>  $RDBBDR/conf/${nconf}
echo "rport=${rport}"   >>  $RDBBDR/conf/${nconf}
echo "rpwd=${rpwd}"     >>  $RDBBDR/conf/${nconf}
echo "rdb=${rdb}"       >>  $RDBBDR/conf/${nconf}
echo "walqtrunc=10000000"  >>  $RDBBDR/conf/${nconf}
echo "batch_size=1000"  >>  $RDBBDR/conf/${nconf}
echo "loglevel=FINE "   >>  $RDBBDR/conf/${nconf}


date
message "Run join node to primary node - host:" ${host} " user:" ${user}" port:" ${port}" pwd:"  ${pwd}" db:" ${db} "node:" ${node} "primary:" ${primary} "ismmr:" ${ismmr}

#echo "create table bo (aa int);" |PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
#PGPASSWORD=${pwd} dropdb -U ${user} -h ${host} -p ${port} ${db}
#PGPASSWORD=${pwd} createdb -U ${user} -h ${host} -p ${port} ${db}
#echo "drop schema _rdb_bdr cascade" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
#echo "create schema _rdb_bdr" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}

echo "ALTER ROLE rdbbdr_user IN DATABASE ${db}  SET search_path TO _rdb_bdr;"| PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}


sed "s/walq__slave/${walqs}/g"   ${RDBBDR}/sql/joining_node_structure_stampo_slave_rdbbdr.sql  > ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}_parz.sql

sed "s/walq__master/${walqp}/g" ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}_parz.sql > ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}.sql 

subtitle "review  ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}.sql , before execute it "
echo "press a to continue"
read a 


PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db} <  ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}.sql

echo "Drop/Creating subscription ${node}_subs_${primary} ${primary}_publ .. "
echo "drop subscription ${node}_subs_${primary} " | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
#echo "create subscription${node}_publ for table _rdb_bdr.${walq};" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}



subtitle "Read conf file for primary node to be joined : "$RDBBDR/conf/${pconf}
read a

if [ ! -f $RDBBDR/conf/${pconf} ]; then
  echo "File $RDBBDR/conf/${pconf}  not found!"
  echo " Command add_remote_node.sh must be run before joining it "
  exit $STATE_UNKNOWN
fi



phost=$(variableInFile host $RDBBDR/conf/${pconf})
pport=$(variableInFile port $RDBBDR/conf/${pconf})
puser=$(variableInFile user $RDBBDR/conf/${pconf})
ppwd=$(variableInFile pwd $RDBBDR/conf/${pconf})
pdb=$(variableInFile db $RDBBDR/conf/${pconf})

rhost=$(variableInFile rhost $RDBBDR/conf/${pconf})
rport=$(variableInFile rport $RDBBDR/conf/${pconf})
ruser=$(variableInFile ruser $RDBBDR/conf/${pconf})
rpwd=$(variableInFile rpwd $RDBBDR/conf/${pconf})
rdb=$(variableInFile rdb $RDBBDR/conf/${pconf})


subtitle "Publication  $phost $pport $puser $pwd $pdb !"
subtitle "Creating subscription ${node}_subs_${primary} "

#
# da gestire il copy_data=false alla creazione della subscri
#

#read -s -p "Enter Password: " pswd
#echo $pswd

#echo "CREATE SUBSCRIPTION ${node}_subs_${primary} CONNECTION 'host=${phost} port=${pport} user=${puser} password=${ppwd} dbname=${pdb}' PUBLICATION ${primary}_publ WITH (connect = true, slot_name = '${primary}_publ_${node}');" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
echo "CREATE SUBSCRIPTION ${node}_subs_${primary} CONNECTION 'host=${rhost} port=${rport} user=${ruser} password=${rpwd} dbname=${rdb}' PUBLICATION ${primary}_publ WITH (connect = true, slot_name = '${primary}_publ_${node}');" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}


echo " insert into  ${walqp}_offset values ('db1',0,0,current_timestamp);"| PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}





