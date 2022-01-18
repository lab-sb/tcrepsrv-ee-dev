#!/bin/sh

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


#RDBBDR=/root/mycode/rdbbdr

#
# Funzione variableInFile()
#

variableInFile () {
    variable=${1}
    file=${2}

    #source ${file}
    . ${file}
    eval value=\$\{${variable}\}
    echo ${value}
}


MyPID=$$
SRV=`hostname`
isok=0

#
# Funzione print_usage()
#

print_usage() {

echo ""
echo "Usage: add_primary_node [--help] [-h host] [-u user] [-p port] [-pwd pwd] [-db database] [-n node] [-rh rhost] [-ru ruser] [-rp rport] [-rpwd pwd] [-rdb database (rdb_db)] "
echo "Usage: add_primary_node [-V]"
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


echo "Read environment file : "$RDBBDR/.rdbbdr_env.sh

if [ ! -f $RDBBDR/.rdbbdr_env.sh ]; then
  echo "File $RDBBDR/.rdbbdr_env.sh  not found!"
  echo " Very Bad ! set RDBBDR variable to HOME of rdbbdr software"
  exit $STATE_UNKNOWN
fi


nodemst=$(variableInFile NODEMST_${node} $RDBBDR/.rdbbdr_env.sh)
echo "(variableInFile NODEMST_${node} ${RDBBDR}/.rdbbdr_env.sh "$nodemst

if [ -z "$nodemst" ]
then
        echo "NODEMST_${node} is unset"
        echo "export NODEMST_${node}=${node}" >> ${RDBBDR}/.rdbbdr_env.sh
else
        echo "NODEMST_${node} substitute with ${node}"
        sed -i 's/NODEMST_${node}=${nodemst}/NODEMST_${node}=${node}/g'  ${RDBBDR}/.rdbbdr_env.sh
fi



read a



## centos
#if [ ! -f /usr/pgsql-10/lib/mydecoder.so ] || [ ! -f /usr/lib/postgresql/10/lib/mydecoder.so ]; then
## ubuntu:
#if [ ! -f /usr/lib/postgresql/10/lib/mydecoder.so ]; then
#  echo "File mydecoder.so not found!"
#  exit $STATE_UNKNOWN
#fi


date
echo "Run add primary node - host:${host} user:${user} port:${port} pwd:${pwd} db:${db} node:${node}  on RDB rhost:${rhost} ruser: ${ruser} rport:${rport} rpwd:${rpwd} rdb:${rdb}" 
echo " "


echo "Checking existance of database ${db}..."

echo "SELECT EXISTS(select 1 from pg_database where datname='${db}') ;" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} 
status=$?

if [ $status -ne 0 ] ; then
	 echo "Invalid credentials provided or datatabase"
	 echo " create user : create user rdbbdr_user  superuser inherit login password 'rdbbdr_pwd'; "
        exit $STATE_UNKNOWN
fi
vv=$(echo "SELECT EXISTS(select 1 from pg_database where datname='${db}') ;" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} )
echo "Esiste database ${db} ? : "$vv

if [ "$vv" = "f" ] ; then
	  echo "Invalid datatabase"
	 echo " create db : create database ${db} ;"
        exit $STATE_UNKNOWN
fi


echo "Checking existance of database ${rdb}..."


echo "SELECT EXISTS(select 1 from pg_database where datname='${rdb}') ;" | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb} 
status=$?

if [ $status -ne 0 ] ; then
         echo "Invalid credentials provided or datatabase"
         echo " create user : create user rdbbdr_user  superuser inherit login password 'rdbbdr_pwd'; "
        exit $STATE_UNKNOWN
fi
vv=$(echo "SELECT EXISTS(select 1 from pg_database where datname='${rdb}') ;" | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb}  )
echo "Esiste database ${rdb} ? : "$vv

if [ "$vv" = "f" ] ; then
          echo "Invalid datatabase"
         echo " create db : create database ${db} ;"
        exit $STATE_UNKNOWN
fi


read a

echo "Creating ${nconf} .."
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





# PGPASSWORD=${pwd} dropdb -U ${user} -h ${host} -p ${port} ${db}

#PGPASSWORD=${pwd} createdb -U ${ruser} -h ${rhost} -p ${rport} ${rdb}

#echo "drop schema _rdb_bdr cascade" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}

#val=$(echo "SELECT EXISTS(select 1 from information_schema.schemata where  schema_name = '_rdb_bdr'); " | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db})

val=$(echo "SELECT EXISTS(select 1 from information_schema.schemata where  schema_name = '_rdb_bdr'); " | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb})

status=$?

echo "Schema _rdb_bdr esiste in ${rdb} ? :" $val

if [ "$val" = "t" ]
then
	echo "schema _rdb_bdr already exists,  Exit !"	
	echo " may be is an already running slave "
	echo " waiting to move _rdb_bdr slave in _bdr_rdb, enter to continue, ctrl-c to stop "
	read a
	#echo "please remove with drop schema _rdb_bdr cascade"
	#echo "also check existing  publication, subscriptions, replication slots"
fi

#echo "drop schema _rdb_bdr cascade" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
#echo "create schema _rdb_bdr" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}

#echo "create schema _rdb_bdr" | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb}

#sed "s/walqueue/${walq}/g"   ${RDBBDR}/sql/primary_node_structure_rdbbdr.sql  > ${RDBBDR}/tmp/primary_node_structure_rdbbdr_${node}.sql


echo " Creating primary node _rdb_bdr structure "

sed "s/walq__master/${walq}/g"   ${RDBBDR}/sql/primary_node_structure_stampo_master_rdbbdr.sql  > ${RDBBDR}/tmp/primary_node_structure_stampo_master_rdbbdr_${node}.sql
PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db} < ${RDBBDR}/tmp/primary_node_structure_stampo_master_rdbbdr_${node}.sql

echo "Finish Creating primary node _rdb_bdr structure , please check ..."
read a

echo " Creating rdb  node _rdb_bdr structure "
sed "s/walq__master/${walq}/g"   ${RDBBDR}/sql/primary_node_structure_stampo_rdb_rdbbdr.sql  >> ${RDBBDR}/tmp/primary_node_structure_stampo_master_rdbbdr_${node}.sql

#PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db} < ${RDBBDR}/tmp/primary_node_structure_rdbbdr_${node}.sql
PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb} < ${RDBBDR}/tmp/primary_node_structure_stampo_master_rdbbdr_${node}.sql


read a

echo "set search_path for rdbbdr_user in ${rdb} ......."

#echo "ALTER ROLE rdbbdr_user IN DATABASE ${db}  SET search_path TO _rdb_bdr;"| PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
echo "ALTER ROLE rdbbdr_user IN DATABASE ${rdb}  SET search_path TO _rdb_bdr;" | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb}

echo "Drop/Creating Publication ${node}_publ .. "
#echo "drop publication ${node}_publ " | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
echo "drop publication ${node}_publ " | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb}
#echo "create publication ${node}_publ for table _rdb_bdr.${walq};" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
echo "create publication ${node}_publ for table _rdb_bdr.${walq};"  | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb} 


echo "Dropping Creating slot ${node}_slot .. "

# rdb_slot_bdr
# da gestire multipli slot per istanza , su diversi database ..." 


# echo " select  pg_drop_replication_slot('rdb_slot_bdr');" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
echo " SELECT * FROM pg_create_logical_replication_slot('rdb_${node}_bdr', 'mydecoder');"  | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}







