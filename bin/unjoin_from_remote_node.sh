#!/bin/sh

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

#RDBBDR=/root/mycode/rdbbdr


MyPID=$$
SRV=`hostname`
isok=0

#
# Funzione print_usage()
#

print_usage() {

echo ""
echo "Usage: join_remote_node [--help] [-h host] [-u user] [-p port] [-pwd pwd] [-db database] [-n node] [-p node] [-f slave/mmr]"
echo "Usage: join_remote_node [-V]"
echo ""

}

echo $@


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


date
echo "Run unjoin node from a primary node - host:" ${host} " user:" ${user}" port:" ${port}" pwd:"  ${pwd}" db:" ${db} "node:" ${node} "primary:" ${primary} "ismmr:" ${ismmr}


variableInFile () {
    variable=${1}
    file=${2}
    #source ${file}
    . ${file}
    eval value=\$\{${variable}\}
    echo ${value}
}

#variableInFile ${@}

echo "Read conf file for primary node to be joined : "$RDBBDR/conf/${pconf}
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

#echo "CREATE SUBSCRIPTION ${node}_subs_${primary} CONNECTION 'host=${phost} port=${pport} user=${puser} password=${ppwd} dbname=${pdb}' PUBLICATION ${primary}_publ WITH (connect = true, slot_name = '${primary}_publ_${node}');" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}

echo "select 'alter subscription '||subname|| ' disable;' from pg_subscription where subname= '${node}_subs_${primary}';" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db}
echo "select 'alter subscription '||subname|| ' set (slot_name = none);' from pg_subscription where subname= '${node}_subs_${primary}';" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db}
echo "select 'drop subscription '||subname|| ' ;' from pg_subscription where subname= '${node}_subs_${primary}';" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db}
echo " select pg_drop_replication_slot('${primary}_publ_${node}');"  | PGPASSWORD=${ppwd} psql -qt -U ${puser} -h ${phost} -p ${pport} ${pdb} 

#echo "drop subscription ${node}_subs_${primary} " | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}






