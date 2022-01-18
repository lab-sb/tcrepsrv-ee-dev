/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */
package com.edslab;

import java.sql.*;
import java.util.Map;
import java.util.Set;
import java.util.Queue;

import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;



//
class TStructures {
    TStructures ( PGReplicationStream str , String w,int tn, boolean fl,int wpt,int wpc, long tx, int a ,Queue<WalTrans> b,Map<Long,Set<String>> c, Map<String,String> d,  Map<Long,Set<Integer>> e, Map<Long,Long> z, Connection f, Connection g , ConnInfo ci, ConnInfo rci){
        stream = str;
        node = w;
         threadn =  tn;
        filter = fl;
        workpthread = wpt;
        workcthread = wpc;
        txidstart = tx;
        batchLimit 	= a;
        queue 	= b;
        map_txidlsn = c;
        map_lsndata = d;
        setTxid 	= e;
        map_minlsn_xid =z;
        connectionRdb = f;
        connection 	= g;
        conninfo =ci;
        rconninfo =rci;
    }
    final  PGReplicationStream stream ;
    final String			node;
    final int               threadn;
    final boolean 			filter;
    final int		        workpthread;
    final int		        workcthread;
    final long			txidstart;
    final int 			batchLimit;
    final Queue<WalTrans> 		queue;
    final Map<Long,Set<String>>	map_txidlsn;
    final Map<String,String> 	map_lsndata;
    final Map<Long,Set<Integer>> 	setTxid ;
    final Map<Long,Long> map_minlsn_xid;
    final Connection 		connectionRdb;
    final Connection 		connection;
    final ConnInfo      conninfo;
    final ConnInfo      rconninfo;

}

