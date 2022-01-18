#!/bin/sh

host=${2}
user=${4}
port=${6}
pwd=${8}
db=${10}
node=${12}
primary=${14}
ismmr=${16}


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
echo "Usage: start_consuming_from_primary [--help] [-h host] [-u user] [-p port] [-pwd pwd] [-db database] [-n node] [-p node] [-f slave/mmr]"
echo "Usage: start_consuming_from_primary [-V]"
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

echo "Drop/Creating subscription ${node}_subs_${primary} ${primary}_publ .. "
echo "drop subscription ${node}_subs_${primary} " | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}


read -p "Enter Publication host: " phost 
read -p "Enter Publication port: " pport 
read -p "Enter Publication user: " puser
read -p "Enter Publication pwd: " ppwd 
read -p "Enter Publication db: " pdb

echo "Publication  $phost $pport $puser $pwd $pdb !"

#read -s -p "Enter Password: " pswd
#echo $pswd

echo "CREATE SUBSCRIPTION ${node}_subs_${primary} CONNECTION 'host=${phost} port=${pport} user=${puser} password=${ppwd} dbname=${pdb}' PUBLICATION ${primary}_publ WITH (connect = true, slot_name = '${primary}_publ_${node}');" | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}








