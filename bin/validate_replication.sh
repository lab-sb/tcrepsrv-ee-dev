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
echo "Usage: validate_replication [--help] [-h host] [-u user] [-p port] [-pwd pwd] [-db database] [-n node] [-p node] [-f slave/mmr]"
echo "Usage: validate_replication [-V]"
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

variableInFile () {
    variable=${1}
    file=${2}

    source ${file}
    eval value=\$\{${variable}\}
    echo ${value}
}



echo "Reading conf file for joined node   ${nconf} .."

nhost=$(variableInFile host $RDBBDR/conf/${nconf})
nport=$(variableInFile port $RDBBDR/conf/${nconf})
nuser=$(variableInFile user $RDBBDR/conf/${nconf})
npwd=$(variableInFile pwd $RDBBDR/conf/${nconf})
ndb=$(variableInFile db $RDBBDR/conf/${nconf})

echo "Read conf file for primary node  : "$RDBBDR/conf/${pconf}
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
pdb=$(variableInFile db $RDBBDR/conf/${nconf})

date
echo "Run validate node to primary node - host:" ${host} " user:" ${user}" port:" ${port}" pwd:"  ${pwd}" db:" ${db} "node:" ${node} "primary:" ${primary} "ismmr:" ${ismmr}


echo "Publication  $phost $pport $puser $pwd $pdb !"


val=$(echo "SELECT EXISTS(select 1 from information_schema.schemata where  schema_name = '_rdb_bdr'); " | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db})

status=$?

echo "val:" $val

if [ $val == "t" ]
then
        echo "validate schema _rdb_bdr on ${node} :exists,   !"
        echo "continuing _rdb_bdr slave in _bdr_rdb, enter to continue, ctrl-c to stop "
        read a
        #echo "also check existing  publication, subscriptions, replication slots"
fi


slavegap=$(echo "SELECT count(*) AS count    FROM walq__${primary} w,    walq__${primary}_offset o  WHERE w.wid > o.last_offset; " | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db})

echo "Find a gap of ${slavegap} from primary"

subs=$(echo "select subname from pg_subscription where subname='${node}_subs_${primary}' and subpublications='{${primary}_publ}' and subenabled='t';" | PGPASSWORD=${pwd} psql -qt -U ${user} -h ${host} -p ${port} ${db})

if [ $subs == "${node}_subs_${primary}" ]
then
        echo "validate subscription on ${node} :exists,   !"
        echo "continuing _rdb_bdr slave in _bdr_rdb, enter to continue, ctrl-c to stop "
        read a
fi


pval=$(echo "SELECT EXISTS(select 1 from information_schema.schemata where  schema_name = '_rdb_bdr'); " | PGPASSWORD=${ppwd} psql -qt -U ${puser} -h ${phost} -p ${pport} ${pdb})

status=$?

echo "val:" $pval

if [ $pval == "t" ]
then
        echo "validate schema _rdb_bdr on ${primary} :exists,   !"
        echo "continuing _rdb_bdr slave in _bdr_rdb, enter to continue, ctrl-c to stop "
        read a
fi

pubs=$(echo "select pubname from _rdb_bdr.sb_publication_rel where relname = 'walq__${primary}';  " | PGPASSWORD=${ppwd} psql -qt -U ${puser} -h ${phost} -p ${pport} ${pdb})

echo "Validating pubs:"$pubs

if [ $pubs == "${primary}_publ" ]
then
        echo "validate publication on ${primary} :exists,   !"
        echo "continuing _rdb_bdr slave in _bdr_rdb, enter to continue, ctrl-c to stop "
        read a
fi


slotval=$(echo "SELECT EXISTS(select * from pg_replication_slots where slot_name = 'rdb_slot_bdr' ); " | PGPASSWORD=${ppwd} psql -qt -U ${puser} -h ${phost} -p ${pport} ${pdb})
if [ $slotval == "t" ]
then
        echo "validate replication_slot on ${primary} :exists,   !"
        echo "continuing _rdb_bdr slave in _bdr_rdb, enter to continue, ctrl-c to stop "
        read a
fi

