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


#RDBBDR=/root/mycode/rdbbdr
#echo "export REMOTEMST_${node}=${node}" >> ${RDBBDR}/.rdbbdr_env.sh 

MyPID=$$
SRV=`hostname`
isok=0

#
# Funzione print_usage()
#

print_usage() {

echo ""
echo "Usage: add_remote_node [--help] [-h host] [-u user] [-p port] [-pwd pwd] [-db database] [-n node]"
echo "Usage: add_remote_node [-V]"
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
echo "rhost=${rhost}" >>  $RDBBDR/conf/${nconf}
echo "ruser=${ruser}" >>  $RDBBDR/conf/${nconf}
echo "rport=${rport}" >>  $RDBBDR/conf/${nconf}
echo "rpwd=${rpwd}" >>  $RDBBDR/conf/${nconf}
echo "rdb=${rdb}" >>  $RDBBDR/conf/${nconf}



date
echo "Run add remote node - host:" ${host} " user:" ${user}" port:" ${port}" pwd:"  ${pwd}" db:" ${db} "node:" ${node} 









