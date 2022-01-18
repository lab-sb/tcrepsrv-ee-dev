
node=mila

echo "Drop/Creating Publication ${node}_publ for table _rdb_bdr.${walq} .. "

val=$(echo "SELECT EXISTS(select 1 from  pg_publication where pubname = '${node}_publ'); " | PGPASSWORD=rdbbdr_pwd psql -qt -U rdbbdr_user -h localhost -p 5432 rdb_db__mila)

status=$?

echo "Publication ${node}_publ esiste  in ${rdb} ?:"$val

if [ "${val}" = " t" ]
then
        echo "Publication ${node}_publ  already exists,  please verify !"
       echo " Press a to continue and drop publication ${node}_publ .."
        #echo "drop publication ${node}_publ " | PGPASSWORD=${pwd} psql -U ${user} -h ${host} -p ${port} ${db}
        echo "drop publication ${node}_publ " | PGPASSWORD=${rpwd} psql -qt -U ${ruser} -h ${rhost} -p ${rport} ${rdb}
        read a
fi

