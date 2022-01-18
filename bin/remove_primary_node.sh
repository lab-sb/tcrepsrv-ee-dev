#!/bin/sh

host=${2}
user=${4}
port=${6}
pwd=${8}
db=${10}
node=${12}

walq=walq__${node}
nconf=${node}_rdb_bdr.conf


#RDBBDR=/root/mycode/rdbbdr



variableInFile () {
    variable=${1}
    file=${2}

    source ${file}
    eval value=\$\{${variable}\}
    echo ${value}
}


#variableInFile ${@}

echo "Read environment file : "$RDBBDR/.rdbbdr_env.sh

if [ ! -f $RDBBDR/.rdbbdr_env.sh ]; then
  echo "File $RDBBDR/.rdbbdr_env.sh  not found!"
  echo " Very Bad ! "
  exit $STATE_UNKNOWN
fi


nodemst=$(variableInFile NODEMST_${node} $RDBBDR/.rdbbdr_env.sh)
echo "(variableInFile NODEMST_${node} ${RDBBDR}/.rdbbdr_env.sh "$nodemst

if [ -z "$nodemst" ] 
then
	echo "NODEMST_${node} is unset"
	echo "export NODEMST_${node}=${node}" >> ${RDBBDR}/.rdbbdr_env.sh 
else
	echo "NODEMST_${node} remove  ${node} from env file"
	sed -i '/NODEMST_${node}/d'   ${RDBBDR}/.rdbbdr_env.sh
fi


read a


MyPID=$$
SRV=`hostname`
isok=0

#
# Funzione print_usage()
#

print_usage() {

echo ""
echo "Usage: add_primary_node [--help] [-h host] [-u user] [-p port] [-pwd pwd] [-db database] [-n node]"
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
echo "Run remove primary node - host:" ${host} " user:" ${user}" port:" ${port}" pwd:"  ${pwd}" db:" ${db} "node:" ${node} 


echo "select pg_drop_replication_slot('rdb_slot_bdr');" |  PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db} 



echo "Remova also schema _rdb_bdr? [Enter]/[CTRL-C]"
read a


echo "drop schema _rdb_bdr cascade" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
echo "drop publication ${node}_publ " | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}

