/**
 * # -- Copyright (c) 2018-2022  Silvio Brandani <support@tcapture.net>. All rights reserved.
 *
 */
package com.edslab;


import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.Queue;
import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

import java.util.stream.*;
import java.util.concurrent.atomic.AtomicBoolean;


import org.apache.commons.cli.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.postgresql.PGConnection;
import org.postgresql.PGProperty;
import org.postgresql.core.BaseConnection;
import org.postgresql.core.ServerVersion;
import org.postgresql.replication.LogSequenceNumber;
import org.postgresql.replication.PGReplicationStream;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.sql.*;
import java.util.Properties;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Date;


class TCleanW implements Runnable {

  private AtomicBoolean running = new AtomicBoolean(false);
  private String mythread = "";
  private static final Logger LOGGERCL= LogManager.getLogger(TCleanW.class.getName());
  final static Object obj = new Object();
	
    private String node ="GC";
    private int threadnum;

   private Map<Long,Set<String>> map_txidlsn;
   private Map<String,String> map_lsndata;

    private Queue<Long> unsetTxid;


    public TCleanW(int threadnum, Map<Long,Set<String>> map_txidlsn ,  Map<String,String> map_lsndata, Queue<Long> unsetTxid) {
     try {

	this.threadnum =threadnum;
	this.map_txidlsn = 	map_txidlsn;
	this.map_lsndata = 	map_lsndata;

	this.unsetTxid = 	unsetTxid;
	 }  catch (Exception ex) {
               LOGGERCL.error( ex.getMessage());
        }


    }

    public void stopRunning()
    {
        running.set(false);
        LOGGERCL.info(  mythread+":  Stop Running");
    }

    public void interrupt() {
        running.set(false);
        Thread.currentThread().interrupt();
        LOGGERCL.info(  mythread+":  Interrupting");

    }

   public void stop() {
        running.set(false);
    }

   boolean isRunning() {
        return running.get();
    }

   public String getMyThread() {
        return mythread;
  }

    public void shutDown()
    {
        running.set(false);
        LOGGERCL.info(  mythread+":  Shutting Down");
    }

  public void cleanUp(Long scanxid) {
	 synchronized(obj) { //synchronized block
                        for (Map.Entry<Long, Set <String>> entry : map_txidlsn.entrySet()) {
                               if( entry.getKey() <  scanxid ) {
                                break;
                               } else {
                                       LOGGERCL.info(mythread + " TXid to manage " + entry.getKey());

                                       Set<String> ilsn  = entry.getValue();
                                       for (String l : ilsn) {
                                           map_lsndata.remove(l);

                                       }
                                       map_txidlsn.remove(entry.getKey());
                                   }

                        }
                  } // End Sync
 
  }

    @Override
    public void run() {
	mythread = "TCleanW_" + threadnum+ "-"+node;
        Thread.currentThread().setName("TCleanW_"+ threadnum+ "-"+node );
        LOGGERCL.info(  mythread+"  is in running state");
	running.set(true);
	Long scanxid;

	 try {
		int sleepn=0;
		int sleepb=10;

	  while (running.get()) {
		scanxid	= unsetTxid.poll(); 
		
		if ( scanxid  == null) {
                    try {

                        Thread.sleep(100);
                         sleepn++;
                        if (sleepn%sleepb == 0)
                        {
                                // LOGGERCL.trace(  mythread+":sleeping");
                                 sleepn = 0;
                        }
                    } catch (InterruptedException e) {

                    }
		 } else {
			 LOGGERCL.trace(mythread + " TXid to unset " + scanxid);
			Set<String> ilsn =  map_txidlsn.get(scanxid);
			 if ( ilsn == null || ilsn.size() == 0 ) {
                                                return;
                          }
              synchronized(obj) { //synchronized block
                  for (String l : ilsn) {
                      map_lsndata.remove(l);

                  }
                  map_txidlsn.remove(scanxid);
                  unsetTxid.remove(scanxid);
              }
      }

	}// end while
	
    } catch(Exception sqlee) {
		   LOGGERCL.trace(mythread+ "Exception" + sqlee.getMessage());
             sqlee.printStackTrace();
    }


  }

}

