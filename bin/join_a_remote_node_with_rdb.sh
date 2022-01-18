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
# variableInFile
#

variableInFile () {
    variable=${1}
    file=${2}

    . ${file}
    eval value=\$\{${variable}\}
    echo ${value}
}



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



echo "Creating ${nconf} .."
echo "host=${host}" >  $RDBBDR/conf/${nconf}
echo "user=${user}" >>  $RDBBDR/conf/${nconf}
echo "port=${port}" >>  $RDBBDR/conf/${nconf}
echo "pwd=${pwd}" >>  $RDBBDR/conf/${nconf}
echo "db=${db}" >>  $RDBBDR/conf/${nconf}
echo "node=${node}" >>  $RDBBDR/conf/${nconf}




date
echo "Run join node to primary node - host:" ${host} " user:" ${user}" port:" ${port}" pwd:"  ${pwd}" db:" ${db} "node:" ${node} "primary:" ${primary} "ismmr:" ${ismmr}

#echo "create table bo (aa int);" |PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
# PGPASSWORD=${pwd} dropdb -U ${user} -h ${host} -p ${port} ${db}
#PGPASSWORD=${pwd} createdb -U ${user} -h ${host} -p ${port} ${db}
#echo "drop schema _rdb_bdr cascade" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
#echo "create schema _rdb_bdr" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}

echo "ALTER ROLE rdbbdr_user IN DATABASE ${db}  SET search_path TO _rdb_bdr;"| PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}


#sed "s/walqueue/${walqs}/g"   ${RDBBDR}/sql/joining_node_structure_rdbbdr.sql  > ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}_parz.sql
sed "s/walq__slave/${walqs}/g"   ${RDBBDR}/sql/joining_node_structure_stampo_slave_rdbbdr.sql  > ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}_parz.sql

#sed "s/walqlon/${walqp}/g" ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}_parz.sql > ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}.sql 
sed "s/walq__master/${walqp}/g" ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}_parz.sql > ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}.sql 

echo "review  ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}.sql , before execute it "
read a 


PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db} <  ${RDBBDR}/tmp/joining_node_structure_rdbbdr_${node}.sql

echo "Drop/Creating subscription ${node}_subs_${primary} ${primary}_publ .. "
echo "drop subscription ${node}_subs_${primary} " | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
#echo "create subscription${node}_publ for table _rdb_bdr.${walq};" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}



echo "Read conf file for primary node to be joined : "$RDBBDR/conf/${pconf}
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


echo "Publication  $phost $pport $puser $pwd $pdb !"

#read -s -p "Enter Password: " pswd
#echo $pswd

#echo "CREATE SUBSCRIPTION ${node}_subs_${primary} CONNECTION 'host=${phost} port=${pport} user=${puser} password=${ppwd} dbname=${pdb}' PUBLICATION ${primary}_publ WITH (connect = true, slot_name = '${primary}_publ_${node}');" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
echo "CREATE SUBSCRIPTION ${node}_subs_${primary} CONNECTION 'host=${rhost} port=${rport} user=${ruser} password=${rpwd} dbname=${rdb}' PUBLICATION ${primary}_publ WITH (connect = true, slot_name = '${primary}_publ_${node}');" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}


echo " insert into  ${walqp}_offset values ('db1',0,0,current_timestamp);"| PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}





