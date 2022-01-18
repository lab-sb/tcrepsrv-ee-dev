#while $(cat ../conf/nx1_rdb_bdr.conf)
 #do 


#if [ "$(awk -F'=' '{print $1}')" = "host" ]
#then 
#	host=$(awk -F'=' '{print $2}')
#fi
#  
#echo $host

 #done


while IFS='' read -r line || [[ -n "$line" ]]; do
    echo "Text read from file: $line"
    variab=$(awk -F'=' '{print $1}')
    valor=$(awk -F'=' '{print $2}')
    echo $variab
    if [ "${variab}" = "host" ]
	then
       host=$valor
    fi

echo $host

done < ../conf/nx1_rdb_bdr.conf
