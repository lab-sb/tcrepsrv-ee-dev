#!/bin/bash

HowMuch=$1
MyPID=$$
FaPID=$PPID
SRV=`hostname`

CHECKRUN=`ps -ef|grep primarytx_incr2 | grep -v ${MyPID}| grep -v ${FaPID} |grep -v  grep|wc -l`

    if [ ${CHECKRUN} -gt 0 ]
    then
        #sleep ${nsleep}
        echo "Still following previous gap.. Exit!"
        exit 0
   fi



# Update primary server trans_id, trans_date
echo $(date '+%Y-%m-%d-%H:%M:%S:%N') " - Starting "
PGPASSWORD=grespost psql -qtX -h localhost -U postgres mil_db -c "update primary_transaction set trans_id=trans_id+1, trans_date=current_timestamp,gap_date=current_timestamp"
PGPASSWORD=grespost psql -qtX -h localhost -U postgres mil_db -c "insert into  primary_transaction_hist select trans_id,trans_date,current_timestamp from primary_transaction;"

# PGPASSWORD=grespost psql -qtX -h localhost -U postgres mil_db -c " do \$\$ BEGIN update primary_transaction set trans_id=trans_id+1, trans_date=current_timestamp,gap_date=current_timestamp; insert into  primary_transaction_hist select trans_id,trans_date,current_timestamp from primary_transaction; end \$\$"


PGPASSWORD=grespost psql -qtX -h localhost -U postgres mil_db -c "INSERT INTO t SELECT generate_series(1,${HowMuch}) AS id, md5((random()*${HowMuch})::text) AS pad, txid_current()"
PGPASSWORD=grespost psql -qtX -h localhost -U postgres mil_db -c "insert into  primary_transaction_hist select max(tx), now(),current_timestamp from t"


GAPP=0
SECS=0
SLP=0.1

        TXID=$(PGPASSWORD=grespost psql -qtX -h localhost -U postgres mil_db -qt -c "select max(tx) from t" )

	ddc=$(date '+%Y-%m-%d-%H:%M:%S:%N')
        echo ">${ddc}  - Checking"
while true
do
        GAPP=$(PGPASSWORD=D3R1bera psql -qtX -h vnycdb-glf01 -U postgres nyc_db -c "select count(*) from primary_transaction_hist where trans_id=$TXID")
        if [ $GAPP -eq 0 ]
        then
                echo  $(date '+%Y-%m-%d-%H:%M:%S:%N') " - Find Gap on Txid  $TXID .."
        else
	     ddf=$(date '+%Y-%m-%d-%H:%M:%S:%N')
             echo "<${ddf}  - Gap filled on Txid $TXID .."
             PGPASSWORD=grespost psql -qtX -h localhost -U postgres mil_db -c "update primary_transaction_hist set gap_date=current_timestamp where trans_id=$TXID"
           #  echo "Gap filled on Txid $TXID .." $(PGPASSWORD=grespost psql -qtX -h localhost -U postgres mil_db -c "select gap_date -trans_date from primary_transaction_hist where trans_id=$TXID;")
             echo $(date '+%Y-%m-%d-%H:%M:%S:%N') " - Finished"
             break
        fi


done
