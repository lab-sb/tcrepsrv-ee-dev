/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */
package com.edslab;

import org.postgresql.replication.PGReplicationStream;

import java.sql.Connection;
import java.util.Map;
import java.util.Queue;
import java.util.Set;


//
class TAStructures {
    TAStructures(String w1, boolean im, String w,int tn, boolean fl, int wpt,int wpc, long tx, int a , Queue<QueueTrans> b, Map<Long,Set<String>> c, Map<String,String> d, Map<String,Long> dw,Map<Long,Set<Integer>> e, Map<Long,Long> z, Connection f, Connection g , ConnInfo ci, ConnInfo rci){
        node = w1;
        isMaster= im;
        nodemst = w;
        threadn =  tn;
        filter = fl;
        workpthread = wpt;
        workcthread = wpc;
        txidstart = tx;
        batchLimit 	= a;
        queue 	= b;
        map_txidlsn = c;
        map_lsndata = d;
        map_lsnwid = dw;
        setTxid 	= e;
        map_minlsn_xid =z;
        connectionRdb = f;
        connection 	= g;
        conninfo =ci;
        rconninfo =rci;
    }

    final String			node;
    final boolean           isMaster;
    final String			nodemst;
    final int               threadn;
    final boolean 			filter;
    final int		        workpthread;
    final int		        workcthread;
    final long			txidstart;
    final int 			batchLimit;
    final Queue<QueueTrans> 		queue;
    final Map<Long,Set<String>>	map_txidlsn;
    final Map<String,String> 	map_lsndata;
    final Map<String,Long> 	map_lsnwid;
    final Map<Long,Set<Integer>> 	setTxid ;
    final Map<Long,Long> map_minlsn_xid;
    final Connection 		connectionRdb;
    final Connection 		connection;
    final ConnInfo      conninfo;
    final ConnInfo      rconninfo;

}

