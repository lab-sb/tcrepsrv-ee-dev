#!/bin/sh
#
# ---------------------------------------------------------------------
# TC CLI unjoin from  primary node.
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
echo "Usage: tc_cli_unjoin_from_primary_node.sh [--help] [-n node] [-p primarynode]"
echo "Usage: tc_cli_unjoin_from_primary_node.sh [-V]"
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
primary=${4}

nconf=${node}_bdr_rdb.conf



host=$(variableInFile host ${RDBBDR}/conf/${nconf})
port=$(variableInFile port ${RDBBDR}/conf/${nconf})
user=$(variableInFile user ${RDBBDR}/conf/${nconf})
pwd=$(variableInFile pwd ${RDBBDR}/conf/${nconf})
db=$(variableInFile db ${RDBBDR}/conf/${nconf})
rdb=$(variableInFile rdb ${RDBBDR}/conf/${nconf})


nconf=${node}_bdr_rdb.conf
pconf=${primary}_rdb_bdr.conf
walqs=walq__${node}
walqp=walq__${primary}

title "-------------------------------------------------------- UNJOINING NODE  ${node} ------------------------------------------------------------------------------------------------"
if [ -z "$node" ]; then
  message " Cannot start script"
  message "Required parameters are missing."
  message " > paramters  "
  message " node:${node}"
  message " "
  print_usage
  exit 1
fi

if [ -z "$primary" ]; then
  message " Cannot start script"
  message "Required parameters are missing."
  message " > paramters  "
  message " node:${node}"
  message " "
  print_usage
  exit 1
fi

echo "Slave   node conf file ${nconf}"  

echo "Primary node conf file ${pconf}"

echo "----------------------------------------------"
echo "Enter to go on OR CTRL-C TO ABORT"
echo "----------------------------------------------"
read a


if [ ! -f $RDBBDR/conf/${nconf} ]; then
  echo "File $RDBBDR/conf/${nconf}  not found!"
  echo " Command add_remote_node.sh must be run before joining it "
  exit $STATE_UNKNOWN
fi


if [ -z "$RDBBDR" ] ; then
  echo "RDBBDR HOME not set !  Should be set in .rdbbdr_env.sh"
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


date
echo "Run unjoin node from a primary node - host:" ${host} " user:" ${user}" port:" ${port}" pwd:"  ${pwd}" db:" ${db} "node:" ${node} "primary:" ${primary} "ismmr:" ${ismmr}


echo "Read conf file for primary node to be unjoined : "$RDBBDR/conf/${pconf}
read a

if [ ! -f $RDBBDR/conf/${pconf} ]; then
  echo "File $RDBBDR/conf/${pconf}  not found!"
  echo " Command add_remote_node.sh must be run before joining it "
  exit $STATE_UNKNOWN
fi

phost=$(variableInFile host ${RDBBDR}/conf/${pconf})
pport=$(variableInFile port ${RDBBDR}/conf/${pconf})
puser=$(variableInFile user ${RDBBDR}/conf/${pconf})
ppwd=$(variableInFile pwd ${RDBBDR}/conf/${pconf})
pdb=$(variableInFile db ${RDBBDR}/conf/${pconf})

echo "Publication  $phost $pport $puser $pwd $pdb !"


echo "SQL:select 'alter subscription '||subname|| ' disable;' from pg_subscription where subname= '${node}_subs_${primary}';| PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${rdb} |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${rdb}"

echo "select 'alter subscription '||subname|| ' disable;' from pg_subscription where subname= '${node}_subs_${primary}';" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${rdb} |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${rdb}
echo "select 'alter subscription '||subname|| ' set (slot_name = none);' from pg_subscription where subname= '${node}_subs_${primary}';" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${rdb} |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${rdb}
echo "select 'drop subscription '||subname|| ' ;' from pg_subscription where subname= '${node}_subs_${primary}';" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${rdb} |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${rdb}

echo "SQL:select pg_drop_replication_slot('${primary}_publ_${node}');  | PGPASSWORD=${ppwd} psql -qt -U ${puser} -h ${phost} -p ${pport} ${pdb}" 
echo " select pg_drop_replication_slot('${primary}_publ_${node}');"  | PGPASSWORD=${ppwd} psql -qt -U ${puser} -h ${phost} -p ${pport} ${pdb} 

#echo "drop subscription ${node}_subs_${primary} " | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}






